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

import com.chris64233.loancovenant.api.dto.DrawdownView;
import com.chris64233.loancovenant.api.dto.LedgerEntryView;
import com.chris64233.loancovenant.api.dto.RepaymentRequest;
import com.chris64233.loancovenant.api.dto.SubmitDrawdownRequest;
import com.chris64233.loancovenant.service.DrawdownService;

@RestController
@RequestMapping("/api/facilities/{facilityId}/drawdowns")
public class DrawdownController {

    private final DrawdownService drawdownService;

    public DrawdownController(DrawdownService drawdownService) {
        this.drawdownService = drawdownService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DrawdownView submit(@PathVariable Long facilityId,
                               @Valid @RequestBody SubmitDrawdownRequest request) {
        return drawdownService.submit(facilityId, request);
    }

    @GetMapping
    public List<DrawdownView> list(@PathVariable Long facilityId) {
        return drawdownService.list(facilityId);
    }

    @GetMapping("/{businessNo}")
    public DrawdownView get(@PathVariable Long facilityId, @PathVariable String businessNo) {
        return drawdownService.get(facilityId, businessNo);
    }

    /** 批准：校验契约、有效期、剩余额度并一次性占用。 */
    @PostMapping("/{businessNo}/approve")
    public DrawdownView approve(@PathVariable Long facilityId, @PathVariable String businessNo) {
        return drawdownService.approve(facilityId, businessNo);
    }

    /** 拨付。 */
    @PostMapping("/{businessNo}/disburse")
    public DrawdownView disburse(@PathVariable Long facilityId, @PathVariable String businessNo) {
        return drawdownService.disburse(facilityId, businessNo);
    }

    /** 取消已批准未拨付提款，释放额度。 */
    @PostMapping("/{businessNo}/cancel")
    public DrawdownView cancel(@PathVariable Long facilityId, @PathVariable String businessNo) {
        return drawdownService.cancel(facilityId, businessNo);
    }

    /** 还款：减少已拨付提款占用的已用额度。 */
    @PostMapping("/{businessNo}/repayments")
    public DrawdownView repay(@PathVariable Long facilityId, @PathVariable String businessNo,
                              @Valid @RequestBody RepaymentRequest request) {
        return drawdownService.repay(facilityId, businessNo, request);
    }

    /** 额度台账（不可变，按记账顺序）。 */
    @GetMapping("/ledger")
    public List<LedgerEntryView> ledger(@PathVariable Long facilityId) {
        return drawdownService.ledger(facilityId);
    }
}
