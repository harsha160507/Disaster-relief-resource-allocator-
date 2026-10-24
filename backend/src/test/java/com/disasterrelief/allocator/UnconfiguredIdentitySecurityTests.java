package com.disasterrelief.allocator;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import com.disasterrelief.allocator.domain.*;
import com.disasterrelief.allocator.repository.UserProfileRepository;
import com.disasterrelief.allocator.support.TestIdentityProvider;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnconfiguredIdentitySecurityTests {
    private static final TestIdentityProvider provider;
    static {
        try { provider = new TestIdentityProvider(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    // Standard Boot properties may create a decoder; they must not bypass the
    // application's explicit issuer/audience and provisioning configuration.
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> TestIdentityProvider.ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", provider::jwksUri);
    }
    @Autowired MockMvc mvc;
    @Autowired UserProfileRepository users;
    @AfterAll static void stopProvider() { provider.close(); }

    @Test void standardBootDecoderSettingsCannotBypassRequiredApplicationIdentityConfiguration() throws Exception {
        var profile = new UserProfile();
        profile.setExternalIssuer(TestIdentityProvider.ISSUER);
        profile.setExternalSubject("unconfigured-" + UUID.randomUUID());
        profile.setDisplayName("Administrator");
        profile.setRoles(new HashSet<>(Set.of(Role.SYSTEM_ADMIN)));
        users.saveAndFlush(profile);
        String token = provider.token(profile.getExternalSubject());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
