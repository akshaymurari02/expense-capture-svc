package com.harness.expensecapture.exception;

import com.harness.expensecapture.model.dto.ErrorResponse;
import com.harness.expensecapture.util.LogConstants;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every exception to the standard error body. No stack traces leak to clients. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReconciliationConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(final ReconciliationConflictException ex,
            final HttpServletRequest request) {
        log.warn("{} Reconciliation conflict rejected. expected={} actual={} difference={} | {}",
                LogConstants.SVC, ex.getMismatch().expected(), ex.getMismatch().actual(),
                ex.getMismatch().difference(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.withMismatch(HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(), ex.getCode(), ex.getMessage(),
                        request.getRequestURI(), ex.getMismatch()));
    }

    @ExceptionHandler(ReceiptException.class)
    public ResponseEntity<ErrorResponse> handleReceiptException(final ReceiptException ex,
            final HttpServletRequest request) {
        log.warn("{} Request failed. code={} message={} | {}", LogConstants.SVC, ex.getCode(),
                ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ErrorResponse.of(ex.getHttpStatus().value(), ex.getHttpStatus().getReasonPhrase(),
                        ex.getCode(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(final MethodArgumentNotValidException ex,
            final HttpServletRequest request) {
        final String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("{} Payload validation failed. detail={} | {}", LogConstants.SVC, detail,
                request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        ErrorCodes.INVALID_ITEM_PAYLOAD, detail, request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(final MaxUploadSizeExceededException ex,
            final HttpServletRequest request) {
        log.warn("{} Upload rejected: size limit exceeded. | {}", LogConstants.SVC,
                request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        ErrorCodes.FILE_UNSUPPORTED, "Uploaded file exceeds the configured size limit",
                        request.getRequestURI()));
    }

    /**
     * Lets Spring's own handling produce a 404 for unmapped paths and missing static resources. Without this,
     * the catch-all below would turn every unknown URL into a 500.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(final Exception ex, final HttpServletRequest request) {
        log.debug("{} No handler for request. | {}", LogConstants.SVC, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(HttpStatus.NOT_FOUND.value(), HttpStatus.NOT_FOUND.getReasonPhrase(),
                        ErrorCodes.RESOURCE_NOT_FOUND, "No endpoint " + request.getMethod() + " "
                                + request.getRequestURI(), request.getRequestURI()));
    }

    /**
     * A body that Jackson cannot bind (malformed JSON, wrong type for a field) is the caller's error, not
     * ours. Without this the catch-all would report it as 500, telling the client to retry something that can
     * never succeed. Jackson's own message is deliberately not echoed back: it names internal classes.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(final HttpMessageNotReadableException ex,
            final HttpServletRequest request) {
        log.warn("{} Malformed request body. reason={} | {}", LogConstants.SVC,
                ex.getMostSpecificCause().getClass().getSimpleName(), request.getRequestURI());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        ErrorCodes.MALFORMED_BODY, "Request body is missing or not valid JSON",
                        request.getRequestURI()));
    }

    /**
     * Wrong HTTP verb on a real path is a 405, not a 500. {@code Allow} is set because the spec requires it and
     * it tells the caller what to use instead.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(final HttpRequestMethodNotSupportedException ex,
            final HttpServletRequest request) {
        log.warn("{} Method not allowed. method={} | {}", LogConstants.SVC, ex.getMethod(),
                request.getRequestURI());
        final ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        Optional.ofNullable(ex.getSupportedHttpMethods())
                .ifPresent(methods -> builder.allow(methods.toArray(new HttpMethod[0])));
        return builder.body(ErrorResponse.of(HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(), ErrorCodes.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for this endpoint", request.getRequestURI()));
    }

    /** Wrong or missing {@code Content-Type} is a 415, not a 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(final HttpMediaTypeNotSupportedException ex,
            final HttpServletRequest request) {
        log.warn("{} Unsupported media type. contentType={} | {}", LogConstants.SVC, ex.getContentType(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ErrorResponse.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(), ErrorCodes.UNSUPPORTED_MEDIA_TYPE,
                        "Content-Type is not supported for this endpoint", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(final Exception ex, final HttpServletRequest request) {
        log.error("{} Unhandled error. | {}", LogConstants.SVC, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), ErrorCodes.INTERNAL_ERROR,
                        "An unexpected internal error occurred", request.getRequestURI()));
    }
}
