package com.tickethub.payment.controller;

import com.tickethub.payment.common.security.AdminOnly;
import com.tickethub.payment.reconciliation.ReconciliationReport;
import com.tickethub.payment.reconciliation.ReconciliationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S8: run reconciliation on demand and read the latest report.
 *   POST /payments/reconciliation/run
 *   GET  /payments/reconciliation/report
 */
@Tag(name = "Reconciliation")
@AdminOnly
@RestController
@RequestMapping("/payments/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService service;

    @PostMapping("/run")
    public ReconciliationReport run() {
        return service.run();
    }

    @GetMapping("/report")
    public ReconciliationReport report() {
        return service.lastReport();
    }
}
