package br.com.finance.config.observability;

import br.com.finance.Application;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = CorrelationIdFilterTest.TestController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
@ContextConfiguration(classes = Application.class)
@Import({CorrelationIdFilter.class, CorrelationIdFilterTest.TestController.class})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CorrelationIdFilterTest {

    private static final String ENDPOINT = "/test/correlation";

    private final MockMvc mockMvc;

    CorrelationIdFilterTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @SneakyThrows
    void doFilterInternal_generatesCorrelationId_whenHeaderIsProvided() {
        mockMvc.perform(get(ENDPOINT).header(CorrelationIdFilter.HEADER_NAME, "client-request-123"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                CorrelationIdFilter.HEADER_NAME,
                matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            ))
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, not("client-request-123")));
    }

    @Test
    @SneakyThrows
    void doFilterInternal_generatesCorrelationId_whenHeaderIsInvalid() {
        mockMvc.perform(get(ENDPOINT).header(CorrelationIdFilter.HEADER_NAME, "invalid value"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                CorrelationIdFilter.HEADER_NAME,
                matchesPattern("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
            ))
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, not("invalid value")));
    }

    @RestController
    @RequestMapping("/test/correlation")
    public static class TestController {

        @GetMapping
        ResponseEntity<Void> get() {
            return ResponseEntity.ok().build();
        }
    }
}
