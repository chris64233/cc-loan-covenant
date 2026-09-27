package com.chris64233.loancovenant.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.chris64233.loancovenant.api.dto.CreateFacilityRequest;
import com.chris64233.loancovenant.api.dto.FacilityView;
import com.chris64233.loancovenant.service.FacilityService;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityView create(@Valid @RequestBody CreateFacilityRequest request) {
        return facilityService.create(request);
    }

    @GetMapping
    public List<FacilityView> list() {
        return facilityService.list();
    }

    @GetMapping("/{facilityId}")
    public FacilityView get(@PathVariable Long facilityId) {
        return facilityService.get(facilityId);
    }

    @PostMapping("/{facilityId}/freeze")
    public FacilityView freeze(@PathVariable Long facilityId) {
        return facilityService.freeze(facilityId);
    }

    @PostMapping("/{facilityId}/unfreeze")
    public FacilityView unfreeze(@PathVariable Long facilityId) {
        return facilityService.unfreeze(facilityId);
    }

    @PostMapping("/{facilityId}/close")
    public FacilityView close(@PathVariable Long facilityId) {
        return facilityService.close(facilityId);
    }
}
