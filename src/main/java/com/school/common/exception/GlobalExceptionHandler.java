package com.school.common.exception;

import com.school.common.RequestIdFilter;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central RFC 7807 (application/problem+json) exception handler.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public static final String VALIDATION_ERROR_DETAIL = "Some fields are invalid";
    public static final String ACCESS_DENIED_DETAIL = "You do not have permission to perform this action";
    public static final String OPTIMISTIC_LOCK_DETAIL = "This record was changed by someone else. Reload and try again.";
    public static final String DATA_INTEGRITY_DETAIL = "The data conflicts with existing records";
    public static final String UNEXPECTED_ERROR_DETAIL = "Something went wrong. Please try again later.";
    public static final String AUTH_REQUIRED_DETAIL = "Authentication required. Please log in again.";

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            String message = fieldError.getDefaultMessage() != null
                    ? fieldError.getDefaultMessage()
                    : "Invalid value";
            errors.putIfAbsent(fieldError.getField(), message);
        }
        for (ObjectError objectError : ex.getBindingResult().getGlobalErrors()) {
            String message = objectError.getDefaultMessage() != null
                    ? objectError.getDefaultMessage()
                    : "Invalid value";
            errors.putIfAbsent(objectError.getObjectName(), message);
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_DETAIL);
        problem.setProperty("errors", errors);
        return problemResponseObject(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String paramName = result.getMethodParameter().getParameterName();
            String key = paramName != null ? paramName : "parameter";
            result.getResolvableErrors().forEach(error -> {
                String message = error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value";
                errors.putIfAbsent(key, message);
            });
        });
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_DETAIL);
        problem.setProperty("errors", errors);
        return problemResponseObject(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String name = ex.getPropertyName() != null ? ex.getPropertyName() : "parameter";
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + name + "'"
        );
        return problemResponseObject(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed JSON request body"
        );
        return problemResponseObject(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing"
        );
        return problemResponseObject(problem, headers);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request
    ) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            attachRequestId(problem);
            return ResponseEntity.status(response.getStatusCode())
                    .headers(response.getHeaders())
                    .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .body(problem);
        }
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String field = extractLeafProperty(violation.getPropertyPath().toString());
            errors.putIfAbsent(field, violation.getMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, VALIDATION_ERROR_DETAIL);
        problem.setProperty("errors", errors);
        return problemResponse(problem);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> handlePropertyReference(PropertyReferenceException ex) {
        return problemResponse(
                HttpStatus.BAD_REQUEST,
                "Invalid sort field: " + ex.getPropertyName()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : "Invalid request";
        return problemResponse(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationFailed(AuthenticationFailedException ex) {
        return problemResponse(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problemResponse(HttpStatus.UNAUTHORIZED, AUTH_REQUIRED_DETAIL);
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ProblemDetail> handlePaymentFailed(PaymentFailedException ex) {
        return problemResponse(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return problemResponse(HttpStatus.FORBIDDEN, ACCESS_DENIED_DETAIL);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return problemResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(ConflictException ex) {
        return problemResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex) {
        return problemResponse(HttpStatus.CONFLICT, OPTIMISTIC_LOCK_DETAIL);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        return problemResponse(HttpStatus.CONFLICT, DATA_INTEGRITY_DETAIL);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex) {
        return problemResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        String requestId = RequestIdFilter.currentRequestId();
        log.error("Unhandled exception [requestId={}]", requestId, ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                UNEXPECTED_ERROR_DETAIL
        );
        problem.setProperty("requestId", requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<ProblemDetail> problemResponse(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        return problemResponse(problem);
    }

    private ResponseEntity<ProblemDetail> problemResponse(ProblemDetail problem) {
        attachRequestId(problem);
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ResponseEntity<Object> problemResponseObject(ProblemDetail problem, HttpHeaders headers) {
        attachRequestId(problem);
        return ResponseEntity.status(problem.getStatus())
                .headers(headers)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private void attachRequestId(ProblemDetail problem) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("requestId")) {
            problem.setProperty("requestId", RequestIdFilter.currentRequestId());
        }
    }

    private String extractLeafProperty(String path) {
        if (path == null || path.isBlank()) {
            return "field";
        }
        int lastDot = path.lastIndexOf('.');
        return lastDot >= 0 && lastDot < path.length() - 1 ? path.substring(lastDot + 1) : path;
    }
}
