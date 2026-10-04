package com.aegis.proxy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aegis.common.config.SecurityConfig;
import com.aegis.common.error.GlobalExceptionHandler;
import com.aegis.common.security.SecurityErrorWriter;
import com.aegis.idempotency.IdempotencyService;
import com.aegis.messaging.ProxyEventPublisher;
import com.aegis.route.RouteNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ProxyController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, GlobalExceptionHandler.class, SecurityErrorWriter.class})
class ProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private IdempotencyService idempotencyService;

    @MockitoBean
    private ProxyEventPublisher proxyEventPublisher;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void unknownRouteReturns404() throws Exception {
        when(proxyService.proxy(eq("no-such"), any(), any(), any(), any(), any()))
                .thenThrow(new RouteNotFoundException("Route not found: no-such"));

        mockMvc.perform(get("/api/v1/proxy/no-such")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void disabledRouteReturns503() throws Exception {
        when(proxyService.proxy(eq("off"), any(), any(), any(), any(), any()))
                .thenThrow(new RouteDisabledException("off"));

        mockMvc.perform(get("/api/v1/proxy/off")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ROUTE_DISABLED"));
    }

    @Test
    void downstreamErrorReturns502() throws Exception {
        when(proxyService.proxy(eq("svc"), any(), any(), any(), any(), any()))
                .thenThrow(new DownstreamException("svc", new RuntimeException("refused")));

        mockMvc.perform(get("/api/v1/proxy/svc")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DOWNSTREAM_ERROR"));
    }

    @Test
    void successfulProxyReturnsDownstreamStatus() throws Exception {
        byte[] responseBody = "{\"id\":1}".getBytes();
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);

        when(proxyService.proxy(eq("orders"), any(), any(), eq(HttpMethod.GET), any(), any()))
                .thenReturn(ResponseEntity.ok().headers(responseHeaders).body(responseBody));

        mockMvc.perform(get("/api/v1/proxy/orders/orders/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VIEWER"))))
                .andExpect(status().isOk());
    }

    @Test
    void postWithBodyIsForwarded() throws Exception {
        byte[] responseBody = "{\"created\":true}".getBytes();

        when(proxyService.proxy(eq("orders"), any(), any(), eq(HttpMethod.POST), any(), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(responseBody));

        mockMvc.perform(post("/api/v1/proxy/orders/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"book\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/proxy/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
