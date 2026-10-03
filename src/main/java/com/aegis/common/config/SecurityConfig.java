package com.aegis.common.config;

import com.aegis.common.security.SecurityErrorWriter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter,
            SecurityErrorWriter securityErrorWriter)
            throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> securityErrorWriter.write(
                                response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) -> securityErrorWriter.write(
                                response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Insufficient permissions")))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/api/v1/auth/token", "/api/v1/status")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/routes", "/api/v1/routes/**")
                        .hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/routes", "/api/v1/routes/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/routes", "/api/v1/routes/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/routes", "/api/v1/routes/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/proxy/**")
                        .hasAnyRole("ADMIN", "OPERATOR", "VIEWER")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            Collection<GrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                    .filter(Objects::nonNull)
                    .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
