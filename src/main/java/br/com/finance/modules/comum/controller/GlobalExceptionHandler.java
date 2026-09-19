package br.com.finance.modules.comum.controller;

import br.com.finance.modules.comum.controller.response.ErrorResponse;
import br.com.finance.modules.comum.controller.response.ValidationErrorResponse;
import br.com.finance.modules.comum.exception.InvalidCredentialsException;
import br.com.finance.modules.comum.exception.LoginRateLimitExceededException;
import br.com.finance.modules.comum.exception.RegistrationRateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static br.com.finance.config.observability.CorrelationIdFilter.REQUEST_ATTRIBUTE_CORRELATION_ID;
import static org.springframework.http.HttpStatus.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_VALUE = "Invalid value";
    private static final String REQUEST_FIELD = "request";

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> handleInvalidCredentials(HttpServletRequest request) {
        return ResponseEntity
            .status(UNAUTHORIZED)
            .body(error(UNAUTHORIZED, "Invalid email or password", request));
    }

    @ExceptionHandler({LoginRateLimitExceededException.class, RegistrationRateLimitExceededException.class})
    ResponseEntity<ErrorResponse> handleAuthenticationRateLimit(HttpServletRequest request) {
        return ResponseEntity
            .status(TOO_MANY_REQUESTS)
            .body(error(TOO_MANY_REQUESTS, "Too many authentication attempts", request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return ResponseEntity
            .badRequest()
            .body(validationError(constraintViolations(ex), request));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode statusCode,
                                                                  @NonNull WebRequest request) {

        var servletRequest = servletRequest(request);

        return new ResponseEntity<>(
            validationError(bindingViolations(ex.getBindingResult()), servletRequest),
            headers,
            BAD_REQUEST
        );
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode statusCode,
                                                                  @NonNull WebRequest request) {

        var servletRequest = servletRequest(request);

        return new ResponseEntity<>(
                error(BAD_REQUEST, "Malformed JSON request", servletRequest),
                headers,
                BAD_REQUEST
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex, HttpServletRequest request) {
        log.atError()
            .setCause(ex)
            .addKeyValue("exceptionType", ex.getClass().getName())
            .log("Unhandled request failure");

        return ResponseEntity
            .status(INTERNAL_SERVER_ERROR)
            .body(error(INTERNAL_SERVER_ERROR, "An unexpected error occurred", request));
    }

    @Override
    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(@NonNull Exception ex,
                                                             @Nullable Object body,
                                                             @NonNull HttpHeaders headers,
                                                             @NonNull HttpStatusCode statusCode,
                                                             @NonNull WebRequest request) {

        var status = HttpStatus.resolve(statusCode.value());
        var servletRequest = servletRequest(request);

        if (status == null) {
            status = INTERNAL_SERVER_ERROR;
        }

        return new ResponseEntity<>(
            error(status, safeMessage(status), servletRequest),
            headers,
            status
        );
    }

    private ErrorResponse error(HttpStatus status, String message, HttpServletRequest request) {
        return ErrorResponse.of(
            status,
            message,
            request.getRequestURI(),
            correlationId(request),
            List.of()
        );
    }

    private ErrorResponse validationError(List<ValidationErrorResponse> violations, HttpServletRequest request) {
        return ErrorResponse.of(
            BAD_REQUEST,
            "Request validation failed",
            request.getRequestURI(),
            correlationId(request),
            violations
        );
    }

    private List<ValidationErrorResponse> bindingViolations(BindingResult bindingResult) {
        return Stream.concat(
            bindingResult.getFieldErrors().stream().map(error ->
                new ValidationErrorResponse(error.getField(), safeValidationMessage(error.getDefaultMessage()))),
            bindingResult.getGlobalErrors().stream().map(error ->
                new ValidationErrorResponse(REQUEST_FIELD, safeValidationMessage(error.getDefaultMessage()))))
            .distinct()
            .sorted(validationErrorOrder())
            .toList();
    }

    private List<ValidationErrorResponse> constraintViolations(ConstraintViolationException ex) {
        return ex.getConstraintViolations().stream()
                .map(violation ->
                    new ValidationErrorResponse(fieldName(violation.getPropertyPath()), safeValidationMessage(violation.getMessage())
                ))
                .distinct()
                .sorted(validationErrorOrder())
                .toList();
    }

    private Comparator<ValidationErrorResponse> validationErrorOrder() {
        return Comparator
            .comparing(ValidationErrorResponse::field)
            .thenComparing(ValidationErrorResponse::message);
    }

    private String fieldName(Path propertyPath) {
        var fieldName = REQUEST_FIELD;

        for (var node : propertyPath) {
            if (node.getName() != null && !node.getName().isBlank()) {
                fieldName = node.getName();
            }
        }

        return fieldName;
    }

    private String safeValidationMessage(String message) {
        return message == null || message.isBlank()
            ? INVALID_VALUE
            : message;
    }

    private String safeMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Invalid request";
            case NOT_FOUND -> "Resource not found";
            case METHOD_NOT_ALLOWED -> "Method not allowed";
            case NOT_ACCEPTABLE -> "Requested media type is not acceptable";
            case UNSUPPORTED_MEDIA_TYPE -> "Unsupported media type";
            default -> status.is4xxClientError()
                ? "Request could not be processed"
                : "An unexpected error occurred";
        };
    }

    private String correlationId(HttpServletRequest request) {
        var correlationId = request.getAttribute(REQUEST_ATTRIBUTE_CORRELATION_ID);

        return correlationId instanceof String value
            ? value
            : "unavailable";
    }

    private HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }
}
