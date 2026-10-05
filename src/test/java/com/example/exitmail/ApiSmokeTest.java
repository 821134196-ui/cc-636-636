package com.example.exitmail;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** REST API 端到端冒烟:员工→自动回复→转发申请→审批→收信→同步→审计。 */
@QuarkusTest
class ApiSmokeTest {

    @Test
    void fullFlowOverRest() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String leaver = "api-leaver-" + tag + "@corp.example";
        String recv = "api-recv-" + tag + "@corp.example";

        // 员工
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + leaver + "\",\"name\":\"接口离职\",\"department\":\"销售部\"}")
                .when().post("/api/employees").then().statusCode(200);
        given().contentType(ContentType.JSON)
                .body("{\"email\":\"" + recv + "\",\"name\":\"接口接替\",\"department\":\"销售部\"}")
                .when().post("/api/employees").then().statusCode(200);

        Number leaverId = given().get("/api/employees").then().statusCode(200)
                .extract().path("find { it.email == '" + leaver + "' }.id");

        // 离职
        given().contentType(ContentType.JSON).body("{\"status\":\"DEPARTED\",\"actor\":\"人事\"}")
                .when().post("/api/employees/" + leaverId.longValue() + "/status")
                .then().statusCode(200).body("status", is("DEPARTED"));

        // 自动回复
        given().contentType(ContentType.JSON)
                .body("{\"employeeEmail\":\"" + leaver + "\",\"content\":\"我已离职,请联系接替人\","
                        + "\"startTime\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\",\"createdBy\":\"人事\"}")
                .when().post("/api/auto-replies").then().statusCode(200)
                .body("version", is(1)).body("status", is("ACTIVE"));

        // 转发申请 → 待审批 → 审批
        Number ruleId = given().contentType(ContentType.JSON)
                .body("{\"employeeEmail\":\"" + leaver + "\",\"recipientEmail\":\"" + recv + "\","
                        + "\"scope\":\"ALL_BUSINESS\","
                        + "\"startTime\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\","
                        + "\"endDate\":\"" + Instant.now().plus(7, ChronoUnit.DAYS) + "\","
                        + "\"requestedBy\":\"主管\"}")
                .when().post("/api/forward-rules").then().statusCode(200)
                .body("status", is("PENDING_APPROVAL"))
                .extract().path("id");

        given().contentType(ContentType.JSON).body("{\"actor\":\"审批人\"}")
                .when().post("/api/forward-rules/" + ruleId.longValue() + "/approve")
                .then().statusCode(200).body("status", is("ACTIVE"));

        // 收信 + 同步
        given().contentType(ContentType.JSON)
                .body("{\"from\":\"client@customer.example\",\"to\":\"" + leaver + "\","
                        + "\"subject\":\"接口测试业务\",\"body\":\"请处理\",\"privateFlag\":false}")
                .when().post("/api/mailbox/receive").then().statusCode(200);
        given().when().post("/api/sync/run").then().statusCode(200);

        // 发件箱应包含自动回复与转发
        given().get("/api/mailbox/outbox").then().statusCode(200)
                .body("findAll { it.kind == 'AUTO_REPLY' && it.fromAddress == '" + leaver + "' }", hasSize(1))
                .body("findAll { it.kind == 'FORWARD' && it.toAddress == '" + recv + "' }", hasSize(1));

        // 审计留痕
        given().get("/api/audit?category=APPROVAL").then().statusCode(200)
                .body("findAll { it.action == 'RULE_APPROVE' }", not(empty()));
        given().get("/api/audit?category=SYNC").then().statusCode(200)
                .body("findAll { it.action == 'FORWARDED' }", not(empty()));

        // 历史版本可查
        given().get("/api/forward-rules/history/" + leaverId.longValue()).then().statusCode(200)
                .body("$", hasSize(1));
        given().get("/api/auto-replies/history/" + leaverId.longValue()).then().statusCode(200)
                .body("$", hasSize(1));
    }

    @Test
    void invalidRequestsRejected() {
        given().contentType(ContentType.JSON).body("{\"email\":\"\",\"name\":\"\"}")
                .when().post("/api/employees").then().statusCode(400);
        given().contentType(ContentType.JSON)
                .body("{\"employeeEmail\":\"nobody@corp.example\",\"content\":\"x\","
                        + "\"startTime\":\"" + Instant.now() + "\"}")
                .when().post("/api/auto-replies").then().statusCode(404);
    }
}
