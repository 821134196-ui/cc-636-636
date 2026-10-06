package com.corp.offboarding;

import com.corp.offboarding.api.AdminResource;
import com.corp.offboarding.entity.Employee;
import com.corp.offboarding.entity.ForwardingRule;
import com.corp.offboarding.entity.MailMessage;
import com.corp.offboarding.entity.SyncTask;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

/**
 * 集成测试基类：每个用例前清库并重新播种（%test 下调度器关闭，时间由用例手动驱动）。
 * fresh* 读取前先清空持久化上下文，避免同测试方法内早先加载的托管实体
 * 遮蔽其他事务（REQUIRES_NEW）已提交的最新状态。
 */
@QuarkusTest
public abstract class AbstractApiTest {

    @Inject
    AdminResource admin;

    @Inject
    EntityManager em;

    @BeforeEach
    void resetData() {
        admin.reset();
        em.clear();
    }

    protected void clearCache() {
        em.clear();
    }

    protected Employee employeeByEmail(String email) {
        return Employee.findByEmail(email);
    }

    protected List<Employee> employees() {
        return Employee.listAll();
    }

    protected MailMessage latestMailOf(Long ownerId) {
        return MailMessage.find("ownerId = ?1 order by id desc", ownerId).firstResult();
    }

    /** 绕过一级缓存，读取规则在数据库中的最新状态。 */
    protected ForwardingRule fwdRule(Long id) {
        em.clear();
        return ForwardingRule.findById(id);
    }

    /** 绕过一级缓存，读取同步任务在数据库中的最新状态。 */
    protected SyncTask freshTask(Long id) {
        em.clear();
        return SyncTask.findById(id);
    }
}
