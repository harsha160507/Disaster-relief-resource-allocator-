package com.disasterrelief.allocator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointRejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/inventory"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointAllowsAuthenticatedRequestPastSecurity() throws Exception {
        mockMvc.perform(get("/api/v1/requests").with(user("field-agent").roles("FIELD_AGENT")))
            .andExpect(status().isNotFound());
    }

    @Test
    void inventoryEndpointAllowsInventoryManagerRole() throws Exception {
        mockMvc.perform(get("/api/v1/inventory").with(user("inventory-manager").roles("INVENTORY_MANAGER")))
            .andExpect(status().isNotFound());
    }

    @Test
    void inventoryEndpointRejectsFieldAgentRole() throws Exception {
        mockMvc.perform(get("/api/v1/inventory").with(user("field-agent").roles("FIELD_AGENT")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"))
            .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void healthEndpointIsExplicitlyPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isNotFound());
    }
}
