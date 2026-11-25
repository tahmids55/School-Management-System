package com.school.common.exception;

/**
 * 403 with a message the user may see, for ownership checks in services
 * ("You can only view your own records"). Role checks keep using @PreAuthorize.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
