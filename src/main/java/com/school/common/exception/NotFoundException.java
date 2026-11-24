package com.school.common.exception;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String entity, Object id) {
        super(entity + " " + id + " was not found");
    }
}
