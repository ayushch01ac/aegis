package com.aegis.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegis.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RouteApiIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RouteRepository routeRepository;

    @BeforeEach
    void clearRoutes() {
        routeRepository.deleteAll();
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void statusEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void routeCrudLifecycle() throws Exception {
        MvcResult createdResult = mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson("orders-service", "http://orders:8080", 2000, "HIGH")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("orders-service"))
                .andReturn();

        JsonNode created = objectMapper.readTree(createdResult.getResponse().getContentAsString());
        String id = created.get("id").asText();

        mockMvc.perform(get("/api/v1/routes/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeoutMs").value(2000));

        mockMvc.perform(put("/api/v1/routes/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson("orders-service", "http://orders:9090", 3000, "CRITICAL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseUrl").value("http://orders:9090"))
                .andExpect(jsonPath("$.priority").value("CRITICAL"));

        mockMvc.perform(get("/api/v1/routes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete("/api/v1/routes/" + id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/routes/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void duplicateNameReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson("orders-service", "http://orders:8080", 2000, "HIGH")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeJson("orders-service", "http://orders:8081", 2000, "HIGH")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_NAME_CONFLICT"));
    }

    @Test
    void errorsIncludeRequestId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/routes")
                        .header("X-Request-Id", "req-test-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id")).isEqualTo("req-test-1");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("requestId").asText()).isEqualTo("req-test-1");
    }

    private static String routeJson(String name, String baseUrl, int timeoutMs, String priority) {
        return """
                {
                  "name": "%s",
                  "baseUrl": "%s",
                  "timeoutMs": %d,
                  "priority": "%s",
                  "enabled": true
                }
                """
                .formatted(name, baseUrl, timeoutMs, priority);
    }
}
