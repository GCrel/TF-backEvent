package org.gcrel.tfbackevent.report.controller;

import java.util.UUID;

import org.gcrel.tfbackevent.model.User;
import org.gcrel.tfbackevent.report.dto.response.EventAttendanceResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSalesResponse;
import org.gcrel.tfbackevent.report.dto.response.EventSummaryResponse;
import org.gcrel.tfbackevent.report.dto.response.OrganizerSummaryResponse;
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

    @GetMapping("/organizer/summary")
    public ResponseEntity<OrganizerSummaryResponse> organizerSummary(@AuthenticationPrincipal User organizer) {
        return ResponseEntity.ok(reportService.organizerSummary(organizer));
    }

    @GetMapping("/events/{id}/sales")
    public ResponseEntity<EventSalesResponse> eventSales(
            @PathVariable UUID id, @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(reportService.eventSales(id, viewer));
    }

    @GetMapping("/events/{id}/attendance")
    public ResponseEntity<EventAttendanceResponse> eventAttendance(
            @PathVariable UUID id, @AuthenticationPrincipal User viewer) {
        return ResponseEntity.ok(reportService.eventAttendance(id, viewer));
    }
}
