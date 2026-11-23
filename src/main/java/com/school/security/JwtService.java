package com.school.security;

import com.school.user.AppUser;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Issues signed HS256 JWT access tokens with standard claims required by the API.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    public JwtService(JwtEncoder jwtEncoder, SecurityProperties securityProperties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    public String generateAccessToken(AppUser user) {
        SecurityProperties.Jwt jwtProps = securityProperties.getJwt();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(jwtProps.getAccessTokenTtl());

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProps.getIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("uid", user.getId())
                .claim("name", user.getFullName())
                .claim("roles", List.of(user.getRole().name()))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenExpiresInSeconds() {
        return securityProperties.getJwt().getAccessTokenTtl().toSeconds();
    }
}
