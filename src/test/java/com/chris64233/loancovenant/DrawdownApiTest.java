package com.chris64233.loancovenant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.chris64233.loancovenant.api.dto.CovenantSpec;
import com.chris64233.loancovenant.api.dto.CreateFacilityRequest;
import com.chris64233.loancovenant.domain.CovenantOperator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DrawdownApiTest extends AbstractIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setup() {
        fixClock();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void fullFlowOverHttp() throws Exception {
        // 创建授信
        String facilityBody = """
                {
                  "customerName": "API企业",
                  "currency": "CNY",
                  "totalLimit": 5000000,
                  "effectiveFrom": "2026-01-01",
                  "effectiveTo": "2026-12-31",
                  "covenants": [
                    {"metricCode": "debtRatio", "displayName": "资产负债率",
                     "operator": "AT_MOST", "threshold": 0.7}
                  ]
                }
                """;
        String facilityResp = mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON).content(facilityBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableLimit").value(5000000.0000))
                .andReturn().getResponse().getContentAsString();
        long facilityId = com.chris64233.loancovenant.support.Json.readLong(facilityResp, "id");

        // 快照 v1
        String snapshotBody = """
                {"reportPeriod": "2026-06-30", "metrics": {"debtRatio": 0.6}}
                """;
        String snapResp = mockMvc.perform(
                        post("/api/facilities/" + facilityId + "/snapshots")
                                .contentType(MediaType.APPLICATION_JSON).content(snapshotBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andReturn().getResponse().getContentAsString();
        long snapshotId = com.chris64233.loancovenant.support.Json.readLong(snapResp, "id");

        // 契约判断
        mockMvc.perform(get("/api/facilities/{f}/snapshots/{s}/covenant-check",
                        facilityId, snapshotId).param("evaluatedOn", "2026-09-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allCovenantsSatisfied").value(true))
                .andExpect(jsonPath("$.effective").value(true));

        // 提交 + 批准
        mockMvc.perform(post("/api/facilities/{f}/drawdowns", facilityId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo": "HTTP-1", "snapshotId": %d, "amount": 2000000}
                                """.formatted(snapshotId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/facilities/{f}/drawdowns/HTTP-1/approve", facilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvalEvidence").exists());

        // 余额查询
        mockMvc.perform(get("/api/facilities/{f}", facilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedLimit").value(2000000.0000))
                .andExpect(jsonPath("$.availableLimit").value(3000000.0000));

        // 超额度：第二笔 400 万
        mockMvc.perform(post("/api/facilities/{f}/drawdowns", facilityId)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"businessNo": "HTTP-2", "snapshotId": %d, "amount": 4000000}
                                """.formatted(snapshotId)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/facilities/{f}/drawdowns/HTTP-2/approve", facilityId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"));

        // 台账两条（只有 HTTP-1 成功）
        mockMvc.perform(get("/api/facilities/{f}/drawdowns/ledger", facilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].drawdownBusinessNo").value("HTTP-1"));
    }

    @Test
    void validationErrorsReturn400() throws Exception {
        String body = """
                {
                  "customerName": "",
                  "currency": "CN",
                  "totalLimit": 0,
                  "effectiveFrom": "2026-12-31",
                  "effectiveTo": "2026-01-01",
                  "covenants": []
                }
                """;
        mockMvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unknownFacilityReturns404() throws Exception {
        mockMvc.perform(get("/api/facilities/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
