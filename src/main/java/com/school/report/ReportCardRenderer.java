package com.school.report;

/**
 * Template Method base class defining the fixed section order for rendering a {@link ReportCard}.
 */
public abstract class ReportCardRenderer {

    public final byte[] render(ReportCard card) {
        begin(card);
        writeHeader(card);
        writeStudentDetails(card);
        writeSubjects(card);
        writeSummary(card);
        writeFooter(card);
        return finish();
    }

    protected abstract void begin(ReportCard card);

    protected abstract void writeHeader(ReportCard card);

    protected abstract void writeStudentDetails(ReportCard card);

    protected abstract void writeSubjects(ReportCard card);

    protected abstract void writeSummary(ReportCard card);

    /** Optional hook overridden by renderers that include a document footer (e.g. PDF). */
    protected void writeFooter(ReportCard card) {
    }

    protected abstract byte[] finish();

    protected static String positionText(ReportCard card) {
        if (card.getPosition() == null) {
            return "-";
        }
        return card.getClassSize() > 0
                ? card.getPosition() + " of " + card.getClassSize()
                : String.valueOf(card.getPosition());
    }
}
