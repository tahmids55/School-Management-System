package com.school.report;

import com.school.report.ReportCardService.RenderedReport;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/exams")
public class ReportCardController {

    private final ReportCardService reportCardService;

    public ReportCardController(ReportCardService reportCardService) {
        this.reportCardService = reportCardService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}/students/{studentId}/report-card")
    public ResponseEntity<byte[]> download(
            @PathVariable Long id,
            @PathVariable Long studentId,
            @RequestParam(defaultValue = "PDF") ReportFormat format
    ) {
        RenderedReport report = reportCardService.generate(id, studentId, format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(report.format().contentType()));
        headers.setContentDisposition(ContentDisposition.attachment().filename(report.fileName()).build());
        return ResponseEntity.ok().headers(headers).body(report.content());
    }
}
