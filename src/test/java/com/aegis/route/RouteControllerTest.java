package com.aegis.route;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegis.common.config.SecurityConfig;
import com.aegis.common.error.GlobalExceptionHandler;
import com.aegis.common.security.SecurityErrorWriter;
import com.aegis.common.web.PagedResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = RouteController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SecurityErrorWriter.class})
class RouteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RouteService routeService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createRejectsInvalidPayload() throws Exception {
        String body =
                """
                {"name":"","baseUrl":"ftp://orders","timeoutMs":-1,"priority":"HIGH"}
                """;

        mockMvc.perform(post("/api/v1/routes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getReturnsMappedRoute() throws Exception {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-02T12:00:00Z");
        when(routeService.get(id))
                .thenReturn(new RouteResponse(
                        id, "orders-service", "http://orders:8080", 2000, RoutePriority.HIGH, true, now, now));

        mockMvc.perform(get("/api/v1/routes/" + id)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("orders-service"))
                .andExpect(jsonPath("$.baseUrl").value("http://orders:8080"));
    }

    @Test
    void listReturnsPagedPayload() throws Exception {
        Instant now = Instant.parse("2026-10-02T12:00:00Z");
        RouteResponse item = new RouteResponse(
                UUID.randomUUID(),
                "orders-service",
                "http://orders:8080",
                2000,
                RoutePriority.HIGH,
                true,
                now,
                now);
        when(routeService.list(any())).thenReturn(new PagedResponse<>(List.of(item), 0, 20, 1, 1));

        mockMvc.perform(get("/api/v1/routes").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("orders-service"));
    }

    @Test
    void rejectsUnauthenticatedRouteAccess() throws Exception {
        mockMvc.perform(get("/api/v1/routes"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsViewerRouteMutation() throws Exception {
        mockMvc.perform(post("/api/v1/routes")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
