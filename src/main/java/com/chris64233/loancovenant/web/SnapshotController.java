package com.chris64233.loancovenant.web;

import com.chris64233.loancovenant.service.CovenantEvaluationView;
import com.chris64233.loancovenant.service.SnapshotInfo;
import com.chris64233.loancovenant.service.SnapshotService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 财务快照提交、查询与契约判断。 */
@RestController
public class SnapshotController {

    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /** 提交财务快照：同一报告期新版本只能追加。 */
    @PostMapping("/api/facilities/{facilityId}/snapshots")
    @ResponseStatus(HttpStatus.CREATED)
    public SnapshotInfo submit(@PathVariable Long facilityId,
                               @Valid @RequestBody SnapshotSubmitRequest req) {
        return snapshotService.submit(new SnapshotService.SubmitCommand(
                facilityId, req.reportingPeriod(), req.metrics()));
    }

    @GetMapping("/api/facilities/{facilityId}/snapshots")
    public List<SnapshotInfo> list(@PathVariable Long facilityId,
                                   @RequestParam(required = false) LocalDate reportingPeriod) {
        return snapshotService.listByFacility(facilityId).stream()
                .filter(s -> reportingPeriod == null
                        || s.reportingPeriod().equals(reportingPeriod))
                .toList();
    }

    /** 契约判断：某快照版本逐项契约结果。 */
    @GetMapping("/api/snapshots/{snapshotId}/covenant-check")
    public CovenantEvaluationView checkCovenants(@PathVariable Long snapshotId) {
        return snapshotService.evaluate(snapshotId);
    }
}
