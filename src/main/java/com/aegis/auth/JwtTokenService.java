package com.aegis.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties securityProperties) {
        this(jwtEncoder, securityProperties, Clock.systemUTC());
    }

    JwtTokenService(JwtEncoder jwtEncoder, SecurityProperties securityProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    public AuthTokenResponse issue(UserAccount user) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(securityProperties.tokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("aegis")
                .subject(user.getUsername())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("roles", List.of(user.getRole().name()))
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new AuthTokenResponse(token, "Bearer", expiresAt);
    }
}
