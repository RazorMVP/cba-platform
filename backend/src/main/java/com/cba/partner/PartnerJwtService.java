package com.cba.partner;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class PartnerJwtService {

    private final byte[] secret;

    public PartnerJwtService(@Value("${app.partner.jwt-secret:partner-dev-secret-key-change-in-production-minimum-256-bits}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String generateToken(PartnerUser user) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getId().toString())
                    .claim("email", user.getEmail())
                    .claim("role", user.getRole())
                    .claim("orgId", user.getOrganization().getId().toString())
                    .claim("orgName", user.getOrganization().getName())
                    .claim("status", user.getOrganization().getStatus().name())
                    .claim("tier", user.getOrganization().getTier())
                    .claim("environment", user.getOrganization().getEnvironment().name())
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(86400)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    public JWTClaimsSet verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) throw new IllegalArgumentException("Invalid signature");
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(Date.from(Instant.now()))) throw new IllegalArgumentException("Token expired");
            return claims;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid partner token", e);
        }
    }
}
