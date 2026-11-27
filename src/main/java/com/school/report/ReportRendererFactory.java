package com.school.report;

import org.springframework.stereotype.Component;

/**
 * Factory returning a fresh {@link ReportCardRenderer} per call because renderers hold per-document state.
 */
@Component
public class ReportRendererFactory {

    public ReportCardRenderer create(ReportFormat format) {
        if (format == null) {
            throw new IllegalArgumentException("Report format is required");
        }
        return switch (format) {
            case PDF -> new PdfReportCardRenderer();
            case CSV -> new CsvReportCardRenderer();
        };
    }
}
