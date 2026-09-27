package com.chris64233.loancovenant.api;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.loancovenant.api.dto.CovenantCheckView;
import com.chris64233.loancovenant.api.dto.SnapshotView;
import com.chris64233.loancovenant.api.dto.SubmitSnapshotRequest;
import com.chris64233.loancovenant.service.SnapshotService;

@RestController
@RequestMapping("/api/facilities/{facilityId}/snapshots")
public class SnapshotController {

    private final SnapshotService snapshotService;

    public SnapshotController(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    /** 提交快照；同一报告期再次提交追加为新版本。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SnapshotView submit(@PathVariable Long facilityId,
                               @Valid @RequestBody SubmitSnapshotRequest request) {
        return snapshotService.submit(facilityId, request);
    }

    @GetMapping
    public List<SnapshotView> list(@PathVariable Long facilityId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period) {
        return period != null
                ? snapshotService.listPeriod(facilityId, period)
                : snapshotService.list(facilityId);
    }

    @GetMapping("/{snapshotId}")
    public SnapshotView get(@PathVariable Long facilityId, @PathVariable Long snapshotId) {
        return snapshotService.get(facilityId, snapshotId);
    }

    /** 契约判断：可通过 evaluatedOn 指定评估日，默认当天。 */
    @GetMapping("/{snapshotId}/covenant-check")
    public CovenantCheckView check(@PathVariable Long facilityId,
                                   @PathVariable Long snapshotId,
                                   @RequestParam(required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                   LocalDate evaluatedOn) {
        return snapshotService.check(facilityId, snapshotId, evaluatedOn);
    }
}
