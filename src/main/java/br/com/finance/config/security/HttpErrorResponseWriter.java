package br.com.finance.config.security;

import br.com.finance.modules.comum.controller.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

import static br.com.finance.config.observability.CorrelationIdFilter.REQUEST_ATTRIBUTE_CORRELATION_ID;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Component
@RequiredArgsConstructor
public class HttpErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                      String message) throws IOException {

        response.setStatus(status.value());
        response.setContentType(APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        var error = ErrorResponse.of(
            status,
            message,
            request.getRequestURI(),
            request.getAttribute(REQUEST_ATTRIBUTE_CORRELATION_ID) instanceof String value ? value : "unavailable",
            List.of()
        );

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
