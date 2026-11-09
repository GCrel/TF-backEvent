package org.gcrel.tfbackevent.report.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse;
import org.gcrel.tfbackevent.report.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/events/{id}/summary")
    public ResponseEntity<EventSummaryResponse> eventSummary(
            @PathVariable UUID id, @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(reportService.eventSummary(id, viewer));
    }
}
