package com.disasterrelief.allocator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.disasterrelief.allocator.api.DisasterController;
import com.disasterrelief.allocator.api.dto.DisasterCreateRequest;
import com.disasterrelief.allocator.domain.Disaster;
import com.disasterrelief.allocator.domain.DisasterType;
import com.disasterrelief.allocator.exception.BusinessRuleViolationException;
import com.disasterrelief.allocator.exception.ResourceNotFoundException;
import com.disasterrelief.allocator.service.DisasterService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = DisasterController.class)
@Import(com.disasterrelief.allocator.config.SecurityConfig.class)
class ApiControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DisasterService disasterService;

    @Test
    void coordinatorCanCreateDisaster() throws Exception {
        Disaster disaster = org.mockito.Mockito.mock(Disaster.class);
        when(disaster.getName()).thenReturn("Flood");
        when(disaster.getType()).thenReturn(DisasterType.FLOOD);
        when(disaster.getStartedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        when(disaster.getAffectedLocations()).thenReturn(java.util.Set.of());
        when(disasterService.create(any(), any(), any(), any())).thenReturn(disaster);

        mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Flood","type":"FLOOD","startedAt":"2026-01-01T00:00:00Z"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Flood"));
    }

    @Test
    void invalidDisasterRequestReturnsConsistentValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

            @Test
            void validationFailureReturnsFieldErrors() throws Exception {
            mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FLOOD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.startedAt").exists());
            }

            @Test
            void notFoundFailureReturnsConsistentError() throws Exception {
            when(disasterService.find(any())).thenThrow(new ResourceNotFoundException("Disaster not found"));

            mockMvc.perform(get("/api/v1/disasters/{id}", UUID.randomUUID())
                .with(user("coordinator").roles("DISASTER_COORDINATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Disaster not found"));
            }

            @Test
            void invalidOperationReturnsUnprocessableEntity() throws Exception {
            when(disasterService.create(any(), any(), any(), any()))
                .thenThrow(new BusinessRuleViolationException("Disaster type and start time are required"));

            mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Flood\",\"type\":\"FLOOD\",\"startedAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_OPERATION"));
            }

            @Test
            void persistenceConflictReturnsConflictWithoutInternalDetails() throws Exception {
            when(disasterService.create(any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("constraint secret details"));

            mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Flood\",\"type\":\"FLOOD\",\"startedAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").value("The resource was changed or conflicts with an existing resource"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
            }

            @Test
            void unexpectedFailureReturnsSafeInternalError() throws Exception {
            when(disasterService.create(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("internal secret details"));

            mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Flood\",\"type\":\"FLOOD\",\"startedAt\":\"2026-01-01T00:00:00Z\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
            }

    @Test
    void fieldAgentCannotCreateDisaster() throws Exception {
        mockMvc.perform(post("/api/v1/disasters")
                .with(user("field-agent").roles("FIELD_AGENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
                .andExpect(status().isForbidden());
    }
}