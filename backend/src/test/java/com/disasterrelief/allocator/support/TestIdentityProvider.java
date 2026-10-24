package com.disasterrelief.allocator.support;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

/** A real local JWKS endpoint and RSA signer; never part of application sources. */
public final class TestIdentityProvider implements AutoCloseable {
    public static final String ISSUER = "https://identity.example.test";
    public static final String AUDIENCE = "relief-api";
    private final KeyPair keys;
    private final HttpServer server;

    public TestIdentityProvider() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        var jwk = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).keyID("phase-two-key").build();
        byte[] body = ("{\"keys\":[" + jwk.toJSONString() + "]}").getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
    }

    public String jwksUri() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks"; }
    public String token(String subject) { return token(subject, builder -> {}); }
    public String token(String subject, Consumer<JWTClaimsSet.Builder> customize) {
        try {
            var builder = new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject)
                    .audience(AUDIENCE).issueTime(Date.from(Instant.now().minusSeconds(5)))
                    .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                    // Deliberately claim admin: the application must ignore token roles.
                    .claim("roles", List.of("SYSTEM_ADMIN"));
            customize.accept(builder);
            var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID("phase-two-key").build(), builder.build());
            token.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return token.serialize();
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    @Override public void close() { server.stop(0); }
}
