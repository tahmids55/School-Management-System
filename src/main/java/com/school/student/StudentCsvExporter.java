package com.school.student;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Writes students as CSV (RFC 4180): every value quoted, quotes doubled, lines end with CRLF.
 * Values starting with = + - @ get a leading ' so Excel shows them as text instead of running
 * them as a formula (CSV injection).
 */
@Component
public class StudentCsvExporter {

    static final String HEADER = "admission_no,first_name,last_name,gender,date_of_birth,class,roll_no,status,"
            + "email,phone,guardian_name,guardian_phone,guardian_relation,admission_date";
    static final String LINE_END = "\r\n";

    /** The students' classes must already be loaded. */
    public String toCsv(List<Student> students) {
        StringBuilder csv = new StringBuilder(HEADER).append(LINE_END);
        for (Student s : students) {
            csv.append(String.join(",",
                    quote(s.getAdmissionNo()),
                    quote(s.getFirstName()),
                    quote(s.getLastName()),
                    quote(name(s.getGender())),
                    quote(date(s.getDateOfBirth())),
                    quote(s.getSchoolClass() == null ? null : s.getSchoolClass().getDisplayName()),
                    quote(s.getRollNo() == null ? null : String.valueOf(s.getRollNo())),
                    quote(name(s.getStatus())),
                    quote(s.getEmail()),
                    quote(s.getPhone()),
                    quote(s.getGuardianName()),
                    quote(s.getGuardianPhone()),
                    quote(s.getGuardianRelation()),
                    quote(date(s.getAdmissionDate()))
            )).append(LINE_END);
        }
        return csv.toString();
    }

    /** null -> "" ; say "hi" -> "say ""hi""" ; =1+1 -> "'=1+1" */
    static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        String safe = value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String date(LocalDate value) {
        return Objects.toString(value, null);
    }
}
