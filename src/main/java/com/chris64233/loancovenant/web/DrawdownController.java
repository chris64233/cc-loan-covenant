package com.chris64233.loancovenant.web;

import com.chris64233.loancovenant.service.DrawdownService;
import com.chris64233.loancovenant.service.DrawdownView;
import com.chris64233.loancovenant.service.LedgerEntryView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提款生命周期与额度台账。
 *
 * <p>批准可能出现两种语义不同的非成功结果：
 * <ul>
 *   <li>HTTP 409（冲突）：冻结/快照被更正/额度并发被占，申请仍为 PENDING；</li>
 *   <li>HTTP 422（拒绝）：契约违约/过期/超总额度，申请终态 REJECTED。</li>
 * </ul>
 */
@RestController
public class DrawdownController {

    private final DrawdownService drawdownService;

    public DrawdownController(DrawdownService drawdownService) {
        this.drawdownService = drawdownService;
    }

    @PostMapping("/api/facilities/{facilityId}/drawdowns")
    public ResponseEntity<DrawdownView> submit(@PathVariable Long facilityId,
                                               @Valid @RequestBody DrawdownSubmitRequest req) {
        DrawdownService.SubmitResult result = drawdownService.submit(
                new DrawdownService.SubmitDrawdownCommand(
                        req.businessNo(), facilityId, req.snapshotId(), req.amount()));
        // 幂等重放：已有申请返回 200；新建返回 201。
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.view());
    }

    /** 批准提款。 */
    @PostMapping("/api/drawdowns/{businessNo}/approve")
    public ResponseEntity<DrawdownView> approve(@PathVariable String businessNo) {
        DrawdownView d = drawdownService.approve(businessNo);
        if ("REJECTED".equals(d.status())) {
            return ResponseEntity.unprocessableEntity().body(d);
        }
        return ResponseEntity.ok(d);
    }

    /** 取消已批准未拨付提款，释放额度。 */
    @PostMapping("/api/drawdowns/{businessNo}/cancel")
    public DrawdownView cancel(@PathVariable String businessNo) {
        return drawdownService.cancel(businessNo);
    }

    /** 拨付。 */
    @PostMapping("/api/drawdowns/{businessNo}/disburse")
    public DrawdownView disburse(@PathVariable String businessNo) {
        return drawdownService.disburse(businessNo);
    }

    /** 还款（仅已拨付提款）。 */
    @PostMapping("/api/drawdowns/{businessNo}/repay")
    public DrawdownView repay(@PathVariable String businessNo,
                              @Valid @RequestBody RepayRequest req) {
        return drawdownService.repay(businessNo, req.amount());
    }

    /** 提款状态查询。 */
    @GetMapping("/api/drawdowns/{businessNo}")
    public DrawdownView get(@PathVariable String businessNo) {
        return drawdownService.getByBusinessNo(businessNo);
    }

    @GetMapping("/api/facilities/{facilityId}/drawdowns")
    public List<DrawdownView> list(@PathVariable Long facilityId) {
        return drawdownService.listByFacility(facilityId);
    }

    /** 额度台账查询（不可变流水）。 */
    @GetMapping("/api/facilities/{facilityId}/ledger")
    public List<LedgerEntryView> ledger(@PathVariable Long facilityId) {
        return drawdownService.ledger(facilityId);
    }
}
