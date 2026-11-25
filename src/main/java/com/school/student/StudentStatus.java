package com.school.student;

/**
 * Only ACTIVE students sit in a class seat and may log in.
 * Changing to any other status is the "soft delete": the row and its history stay.
 */
public enum StudentStatus {
    ACTIVE,
    INACTIVE,
    GRADUATED,
    TRANSFERRED
}
