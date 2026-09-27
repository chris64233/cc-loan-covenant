package com.chris64233.loancovenant.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chris64233.loancovenant.testsupport.TestClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class DrawdownApiIntegrationTest {

    @Autowired MockMvc mvc;

    private long createFacility() throws Exception {
        String body = """
                {
                  "customerName": "接口企业",
                  "currency": "CNY",
                  "totalLimit": 1000.0000,
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31",
                  "covenants": [
                    {"metricKey": "CURRENT_RATIO", "displayName": "流动比率",
                     "operator": "GTE", "threshold": 1.50}
                  ]
                }
                """;
        MvcResult result = mvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.availableAmount").value(1000.0000))
                .andReturn();
        return Json.readLong(result.getResponse().getContentAsString(), "facilityId");
    }

    private long submitSnapshot(long facilityId, double currentRatio) throws Exception {
        String body = "{\"reportingPeriod\":\"2026-03-31\","
                + "\"metrics\":{\"CURRENT_RATIO\":" + currentRatio + "}}";
        MvcResult result = mvc.perform(post("/api/facilities/{id}/snapshots", facilityId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return Json.readLong(result.getResponse().getContentAsString(), "id");
    }

    @Test
    void full_happy_path_over_http() throws Exception {
        long facilityId = createFacility();
        long snapshotId = submitSnapshot(facilityId, 1.80);

        // 契约判断查询。
        mvc.perform(get("/api/snapshots/{id}/covenant-check", snapshotId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allSatisfied").value(true))
                .andExpect(jsonPath("$.checks[0].metricKey").value("CURRENT_RATIO"));

        String dd = "{\"businessNo\":\"API-DD-1\",\"snapshotId\":" + snapshotId
                + ",\"amount\":400.0000}";
        mvc.perform(post("/api/facilities/{id}/drawdowns", facilityId)
                        .contentType(MediaType.APPLICATION_JSON).content(dd))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 幂等提交：200 + 同一状态。
        mvc.perform(post("/api/facilities/{id}/drawdowns", facilityId)
                        .contentType(MediaType.APPLICATION_JSON).content(dd))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessNo").value("API-DD-1"));

        mvc.perform(post("/api/drawdowns/{no}/approve", "API-DD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decisionBasis").isNotEmpty());

        // 余额与台账。
        mvc.perform(get("/api/facilities/{id}", facilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedAmount").value(400.0000))
                .andExpect(jsonPath("$.availableAmount").value(600.0000))
                .andExpect(jsonPath("$.withinValidity").value(true))
                .andExpect(jsonPath("$.covenants[0].operator").value("GTE"));
        mvc.perform(get("/api/facilities/{id}/ledger", facilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("DRAWDOWN_APPROVED"))
                .andExpect(jsonPath("$[0].usedAmountAfter").value(400.0000));

        // 拨付后还款。
        mvc.perform(post("/api/drawdowns/{no}/disburse", "API-DD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"));
        mvc.perform(post("/api/drawdowns/{no}/repay", "API-DD-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":400.0000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));
        mvc.perform(get("/api/facilities/{id}", facilityId))
                .andExpect(jsonPath("$.usedAmount").value(0));
    }

    @Test
    void covenant_breach_returns_422_and_keeps_no_ledger() throws Exception {
        long facilityId = createFacility();
        long snapshotId = submitSnapshot(facilityId, 1.20); // 低于 1.5
        String dd = "{\"businessNo\":\"API-BAD\",\"snapshotId\":" + snapshotId
                + ",\"amount\":10.0000}";
        mvc.perform(post("/api/facilities/{id}/drawdowns", facilityId)
                        .contentType(MediaType.APPLICATION_JSON).content(dd))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/drawdowns/{no}/approve", "API-BAD"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").isNotEmpty());
        mvc.perform(get("/api/facilities/{id}/ledger", facilityId))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void frozen_facility_and_corrected_snapshot_return_409() throws Exception {
        long facilityId = createFacility();
        long snapshotId = submitSnapshot(facilityId, 1.80);
        String dd = "{\"businessNo\":\"API-FROZEN\",\"snapshotId\":" + snapshotId
                + ",\"amount\":10.0000}";
        mvc.perform(post("/api/facilities/{id}/drawdowns", facilityId)
                .contentType(MediaType.APPLICATION_JSON).content(dd)).andExpect(status().isCreated());
        mvc.perform(post("/api/facilities/{id}/freeze", facilityId))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/drawdowns/{no}/approve", "API-FROZEN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FACILITY_FROZEN"));

        // 解冻后更正快照再批准 → SNAPSHOT_SUPERSEDED 409。
        mvc.perform(post("/api/facilities/{id}/unfreeze", facilityId))
                .andExpect(status().isNoContent());
        submitSnapshot(facilityId, 2.00);
        mvc.perform(post("/api/drawdowns/{no}/approve", "API-FROZEN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SNAPSHOT_SUPERSEDED"));
        mvc.perform(get("/api/drawdowns/{no}", "API-FROZEN"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.snapshotStatus").value("SUPERSEDED"));
    }

    @Test
    void not_found_and_validation_errors_are_mapped() throws Exception {
        mvc.perform(get("/api/facilities/9999")).andExpect(status().isNotFound());
        mvc.perform(post("/api/drawdowns/NO-SUCH/approve")).andExpect(status().isNotFound());

        String invalid = """
                {
                  "customerName": "",
                  "currency": "CNY",
                  "totalLimit": -1,
                  "startDate": "2026-01-01",
                  "endDate": "2026-12-31",
                  "covenants": []
                }
                """;
        mvc.perform(post("/api/facilities")
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.customerName").exists());
    }
}
