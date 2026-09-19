package br.com.finance.config.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String REQUEST_ATTRIBUTE_CORRELATION_ID =
        CorrelationIdFilter.class.getName() + ".correlationId";

    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        var startedAt = System.nanoTime();
        var correlationId = UUID.randomUUID().toString();

        request.setAttribute(REQUEST_ATTRIBUTE_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_NAME, correlationId);

        try (var ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                logRequest(request, response, startedAt);
            }
        }
    }

    private void logRequest(HttpServletRequest request, HttpServletResponse response, long startedAt) {
        log.atInfo()
            .addKeyValue("httpMethod", request.getMethod())
            .addKeyValue("httpStatus", response.getStatus())
            .addKeyValue("durationMs", elapsedMillis(startedAt))
            .log("HTTP request completed");
    }

    private double elapsedMillis(long startedAt) {
        var elapsedNanos = System.nanoTime() - startedAt;
        return Math.round(elapsedNanos / 1_000.0) / 1_000.0;
    }
}
