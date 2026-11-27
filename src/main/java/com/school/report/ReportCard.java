package com.school.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable report card data model built via the Builder pattern.
 */
public final class ReportCard {

    public record Line(
            String subjectCode,
            String subjectName,
            int fullMarks,
            String obtained,
            String letter,
            BigDecimal point,
            boolean passed
    ) {
    }

    private final String schoolName;
    private final String examName;
    private final String academicYearName;
    private final String gradingScheme;
    private final String studentName;
    private final String admissionNo;
    private final String className;
    private final Integer rollNo;
    private final List<Line> lines;
    private final BigDecimal totalObtained;
    private final int totalFullMarks;
    private final BigDecimal percentage;
    private final BigDecimal gpa;
    private final String letter;
    private final boolean passed;
    private final Integer position;
    private final int classSize;
    private final Instant generatedAt;

    private ReportCard(Builder builder) {
        this.schoolName = builder.schoolName;
        this.examName = builder.examName;
        this.academicYearName = builder.academicYearName != null ? builder.academicYearName : "";
        this.gradingScheme = builder.gradingScheme != null ? builder.gradingScheme : "";
        this.studentName = builder.studentName;
        this.admissionNo = builder.admissionNo != null ? builder.admissionNo : "";
        this.className = builder.className != null ? builder.className : "";
        this.rollNo = builder.rollNo;
        this.lines = List.copyOf(builder.lines);
        this.totalObtained = builder.totalObtained != null ? builder.totalObtained : BigDecimal.ZERO;
        this.totalFullMarks = builder.totalFullMarks;
        this.percentage = builder.percentage != null ? builder.percentage : BigDecimal.ZERO;
        this.gpa = builder.gpa != null ? builder.gpa : BigDecimal.ZERO;
        this.letter = builder.letter;
        this.passed = builder.passed;
        this.position = builder.position;
        this.classSize = builder.classSize;
        this.generatedAt = Objects.requireNonNull(builder.generatedAt, "generatedAt is required");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSchoolName() {
        return schoolName;
    }

    public String getExamName() {
        return examName;
    }

    public String getAcademicYearName() {
        return academicYearName;
    }

    public String getGradingScheme() {
        return gradingScheme;
    }

    public String getStudentName() {
        return studentName;
    }

    public String getAdmissionNo() {
        return admissionNo;
    }

    public String getClassName() {
        return className;
    }

    public Integer getRollNo() {
        return rollNo;
    }

    public List<Line> getLines() {
        return lines;
    }

    public BigDecimal getTotalObtained() {
        return totalObtained;
    }

    public int getTotalFullMarks() {
        return totalFullMarks;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public BigDecimal getGpa() {
        return gpa;
    }

    public String getLetter() {
        return letter;
    }

    public boolean isPassed() {
        return passed;
    }

    public Integer getPosition() {
        return position;
    }

    public int getClassSize() {
        return classSize;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public static final class Builder {

        private String schoolName;
        private String examName;
        private String academicYearName;
        private String gradingScheme;
        private String studentName;
        private String admissionNo;
        private String className;
        private Integer rollNo;
        private final List<Line> lines = new ArrayList<>();
        private BigDecimal totalObtained;
        private int totalFullMarks;
        private BigDecimal percentage;
        private BigDecimal gpa;
        private String letter;
        private boolean passed;
        private Integer position;
        private int classSize;
        private Instant generatedAt;

        private Builder() {
        }

        public Builder schoolName(String schoolName) {
            this.schoolName = schoolName;
            return this;
        }

        public Builder examName(String examName) {
            this.examName = examName;
            return this;
        }

        public Builder academicYearName(String academicYearName) {
            this.academicYearName = academicYearName;
            return this;
        }

        public Builder gradingScheme(String gradingScheme) {
            this.gradingScheme = gradingScheme;
            return this;
        }

        public Builder studentName(String studentName) {
            this.studentName = studentName;
            return this;
        }

        public Builder admissionNo(String admissionNo) {
            this.admissionNo = admissionNo;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder rollNo(Integer rollNo) {
            this.rollNo = rollNo;
            return this;
        }

        public Builder addLine(Line line) {
            this.lines.add(Objects.requireNonNull(line, "line must not be null"));
            return this;
        }

        public Builder lines(List<Line> lines) {
            this.lines.clear();
            if (lines != null) {
                this.lines.addAll(lines);
            }
            return this;
        }

        public Builder totalObtained(BigDecimal totalObtained) {
            this.totalObtained = totalObtained;
            return this;
        }

        public Builder totalFullMarks(int totalFullMarks) {
            this.totalFullMarks = totalFullMarks;
            return this;
        }

        public Builder percentage(BigDecimal percentage) {
            this.percentage = percentage;
            return this;
        }

        public Builder gpa(BigDecimal gpa) {
            this.gpa = gpa;
            return this;
        }

        public Builder letter(String letter) {
            this.letter = letter;
            return this;
        }

        public Builder passed(boolean passed) {
            this.passed = passed;
            return this;
        }

        public Builder position(Integer position) {
            this.position = position;
            return this;
        }

        public Builder classSize(int classSize) {
            this.classSize = classSize;
            return this;
        }

        public Builder generatedAt(Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public ReportCard build() {
            requireNotBlank(schoolName, "schoolName");
            requireNotBlank(examName, "examName");
            requireNotBlank(studentName, "studentName");
            requireNotBlank(letter, "letter");
            if (lines.isEmpty()) {
                throw new IllegalStateException("A report card needs at least one subject");
            }
            if (generatedAt == null) {
                throw new IllegalStateException("generatedAt is required");
            }
            return new ReportCard(this);
        }

        private static void requireNotBlank(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(field + " is required");
            }
        }
    }
}
