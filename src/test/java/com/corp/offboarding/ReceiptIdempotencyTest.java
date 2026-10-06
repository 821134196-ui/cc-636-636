package com.corp.offboarding;

import com.corp.offboarding.entity.*;
import com.corp.offboarding.model.MailScope;
import com.corp.offboarding.service.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
/**
 * 外部邮件服务回执幂等：同一回执号第一次确认，重复回执只记审计并被丢弃，
 * 不会触发重新投递，也不改变规则/任务的任何状态。
 */
class ReceiptIdempotencyTest extends AbstractApiTest {

    @Inject ForwardingService forwarding;
    @Inject AutoReplyService replies;
    @Inject MailIntakeService intake;
    @Inject SyncService sync;
    @Inject TimeService clock;

    @Test
    void duplicateReceiptsAreIdempotentNoops() {
        Employee owner = employeeByEmail("zhangwei@corp.example");
        Employee lina = employeeByEmail("lina@corp.example");
        long now = clock.now();
        var rule = forwarding.request(owner.id, lina.id, MailScope.ALL_BUSINESS,
                null, now, now + 3600_000, "主管");
        forwarding.approve(rule.id, "审批人");

        var mail = intake.receive(owner.id, "buyer@partner.example", "客户",
                "合同", "正文", false, false);
        sync.drain(forwarding, replies);

        ForwardOutcome outcome = ForwardOutcome
                .find("mailId = ?1 and ruleId = ?2", mail.id, rule.id).firstResult();
        String receipt = outcome.receiptId;
        assertNotNull(receipt);

        SyncService.ReceiptAck first = sync.acknowledgeReceipt(receipt, "MAIL-GATEWAY");
        assertTrue(first.first());
        assertEquals("ACKNOWLEDGED", first.result());

        // 重复 3 次：全部幂等丢弃
        for (int i = 0; i < 3; i++) {
            SyncService.ReceiptAck dup = sync.acknowledgeReceipt(receipt, "MAIL-GATEWAY");
            assertFalse(dup.first());
            assertEquals("DUPLICATE_IGNORED", dup.result());
        }

        // 没有新增任何投递或处置结论
        assertEquals(1, ForwardOutcome.count("mailId = ?1 and ruleId = ?2",
                mail.id, rule.id));
        assertEquals(1, MailboxOutbox.count(
                "sourceMailId = ?1 and type = 'FORWARD'", mail.id));

        // 重复回执在审计中留痕
        long dupAudits = AuditLog.count("action = 'SYNC_RECEIPT_DUPLICATE'");
        assertEquals(3, dupAudits);

        // 未知回执号必须报错，而不是被当成有效确认
        assertThrows(jakarta.ws.rs.NotFoundException.class,
                () -> sync.acknowledgeReceipt("RCPT-UNKNOWN-000", "MAIL-GATEWAY"));
    }
}
