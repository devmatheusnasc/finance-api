package br.com.finance.config.exception;

import br.com.finance.Application;
import br.com.finance.config.observability.CorrelationIdFilter;
import br.com.finance.modules.comum.controller.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = GlobalExceptionHandlerTest.TestController.class,
    excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
    }
)
@ContextConfiguration(classes = Application.class)
@Import({
    GlobalExceptionHandler.class,
    CorrelationIdFilter.class,
    GlobalExceptionHandlerTest.TestController.class
})
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID_PATTERN =
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private final MockMvc mockMvc;

    GlobalExceptionHandlerTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    @SneakyThrows
    void handleMethodArgumentNotValid_returnsValidationDetails_whenRequestIsInvalid() {
        mockMvc.perform(post("/test/foundation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, matchesPattern(CORRELATION_ID_PATTERN)))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("Request validation failed"))
            .andExpect(jsonPath("$.path").value("/test/foundation"))
            .andExpect(jsonPath("$.correlationId").value(matchesPattern(CORRELATION_ID_PATTERN)))
            .andExpect(jsonPath("$.violations[0].field").value("name"))
            .andExpect(jsonPath("$.violations[0].message").value("Name is required"));
    }

    @Test
    @SneakyThrows
    void handleHttpMessageNotReadable_returnsSafeReason_whenJsonIsMalformed() {
        mockMvc.perform(post("/test/foundation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Malformed JSON request"))
            .andExpect(jsonPath("$.path").value("/test/foundation"))
            .andExpect(jsonPath("$.correlationId").value(matchesPattern(CORRELATION_ID_PATTERN)))
            .andExpect(jsonPath("$.violations").doesNotExist());
    }

    @Test
    @SneakyThrows
    void handleUnexpectedException_returnsSanitizedError_whenUnexpectedFailureOccurs() {
        mockMvc.perform(get("/test/foundation/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, matchesPattern(CORRELATION_ID_PATTERN)))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
            .andExpect(jsonPath("$.message", not(containsString("sensitive failure detail"))))
            .andExpect(jsonPath("$.correlationId").value(matchesPattern(CORRELATION_ID_PATTERN)));
    }

    @RestController
    @RequestMapping("/test/foundation")
    public static class TestController {

        @PostMapping
        ResponseEntity<Void> validate(@Valid @RequestBody TestRequest request) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/failure")
        ResponseEntity<Void> fail() {
            throw new IllegalStateException("sensitive failure detail");
        }
    }

    public record TestRequest(@NotBlank(message = "Name is required") String name) {
    }
}
