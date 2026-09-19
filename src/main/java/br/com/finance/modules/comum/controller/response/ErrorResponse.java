package br.com.finance.modules.comum.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(

        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId,
        List<ValidationErrorResponse> violations

) {

    public ErrorResponse {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(error, "error must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");

        violations = violations == null
            ? List.of()
            : List.copyOf(violations);
    }

    public static ErrorResponse of(HttpStatus status, String message, String path,
                                   String correlationId, List<ValidationErrorResponse> violations) {

        Objects.requireNonNull(status, "status must not be null");

        return new ErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            path,
            correlationId,
            violations
        );
    }
}
