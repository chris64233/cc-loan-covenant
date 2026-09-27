package com.chris64233.loancovenant.web;

import com.chris64233.loancovenant.service.CreateFacilityCommand;
import com.chris64233.loancovenant.service.FacilityBalanceView;
import com.chris64233.loancovenant.service.FacilityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 授信建立、余额查询与审批冻结。 */
@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityBalanceView create(@Valid @RequestBody CreateFacilityRequest req) {
        CreateFacilityCommand cmd = new CreateFacilityCommand(
                req.customerName(), req.currency(), req.totalLimit(),
                req.startDate(), req.endDate(),
                req.covenants() == null ? List.of() : req.covenants().stream()
                        .map(c -> new CreateFacilityCommand.CovenantSpec(
                                c.metricKey(), c.displayName(), c.operator(), c.threshold()))
                        .toList());
        return facilityService.create(cmd);
    }

    /** 授信余额查询（含契约配置与有效期判定）。 */
    @GetMapping("/{id}")
    public FacilityBalanceView get(@PathVariable Long id) {
        return facilityService.balance(id);
    }

    @PostMapping("/{id}/freeze")
    public ResponseEntity<Void> freeze(@PathVariable Long id) {
        facilityService.freeze(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/unfreeze")
    public ResponseEntity<Void> unfreeze(@PathVariable Long id) {
        facilityService.unfreeze(id);
        return ResponseEntity.noContent().build();
    }
}
