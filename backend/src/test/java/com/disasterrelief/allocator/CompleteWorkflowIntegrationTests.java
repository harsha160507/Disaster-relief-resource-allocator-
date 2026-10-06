package com.disasterrelief.allocator;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.disasterrelief.allocator.domain.Organization;
import com.disasterrelief.allocator.domain.UserProfile;
import com.disasterrelief.allocator.repository.OrganizationRepository;
import com.disasterrelief.allocator.repository.UserProfileRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class CompleteWorkflowIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    private UUID organizationId;
    private UUID requesterId;

    @BeforeEach
    void setUpReferenceData() {
        Organization organization = new Organization();
        organization.setName("Integration Relief " + UUID.randomUUID());
        organization = organizationRepository.save(organization);
        organizationId = organization.getId();

        UserProfile requester = new UserProfile();
        requester.setExternalSubject("integration-field-agent-" + UUID.randomUUID());
        requester.setDisplayName("Integration Field Agent");
        requester.setOrganization(organization);
        requester = userProfileRepository.save(requester);
        requesterId = requester.getId();
    }

    @Test
    void completeWorkflowCreatesAllocatesUpdatesAndRetrievesHistory() throws Exception {
        UUID disasterId = postId("/api/v1/disasters", """
                {"name":"Integration Flood","type":"FLOOD","startedAt":"2026-01-01T00:00:00Z"}
                """, "DISASTER_COORDINATOR");

        UUID locationId = postId("/api/v1/locations", """
                {"name":"Integration Shelter","address":"1 Test Way","organizationId":"%s"}
                """.formatted(organizationId), "PARTNER_MANAGER");

        mockMvc.perform(post("/api/v1/disasters/{id}/activation", disasterId)
                .with(user("coordinator").roles("DISASTER_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(put("/api/v1/disasters/{id}/affected-locations/{locationId}", disasterId, locationId)
                .with(user("coordinator").roles("DISASTER_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.affectedLocationIds[0]").value(locationId.toString()));

        UUID resourceTypeId = postId("/api/v1/resource-types", """
                {"name":"Integration Water","unitOfMeasure":"litres","perishable":false}
                """, "INVENTORY_MANAGER");
        UUID inventoryId = postId("/api/v1/inventory/receipts", """
                {"locationId":"%s","resourceTypeId":"%s","quantity":50}
                """.formatted(locationId, resourceTypeId), "INVENTORY_MANAGER");

        String reference = "REQ-" + UUID.randomUUID().toString().substring(0, 8);
        UUID requestId = postId("/api/v1/requests", """
                {"reference":"%s","disasterId":"%s","requesterId":"%s","destinationId":"%s","priority":"HIGH","notes":"Integration test"}
                """.formatted(reference, disasterId, requesterId, locationId), "FIELD_AGENT");
        UUID requestItemId = UUID.fromString(JsonPath.read(mockMvc.perform(post("/api/v1/requests/{id}/items", requestId)
                .with(user("field-agent").roles("FIELD_AGENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceTypeId\":\"%s\",\"quantity\":20}".formatted(resourceTypeId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.items[0].id"));

        mockMvc.perform(post("/api/v1/requests/{id}/submission", requestId)
                .with(user("field-agent").roles("FIELD_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(post("/api/v1/requests/{id}/approval", requestId)
                .with(user("coordinator").roles("DISASTER_COORDINATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/v1/allocations/requests/{id}", requestId)
                .with(user("inventory-manager").roles("INVENTORY_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestItemId").value(requestItemId.toString()))
                .andExpect(jsonPath("$[0].inventoryId").value(inventoryId.toString()))
                .andExpect(jsonPath("$[0].quantity").value(20));

        mockMvc.perform(get("/api/v1/allocations/request-items/{id}", requestItemId)
                .with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESERVED"))
                .andExpect(jsonPath("$[0].quantity").value(20));

        mockMvc.perform(patch("/api/v1/inventory/{id}", inventoryId)
                .with(user("inventory-manager").roles("INVENTORY_MANAGER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"change\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(60))
                .andExpect(jsonPath("$.reservedQuantity").value(20))
                .andExpect(jsonPath("$.availableQuantity").value(40));

        UUID insufficientRequestId = postId("/api/v1/requests", """
                {"reference":"REQ-%s","disasterId":"%s","requesterId":"%s","destinationId":"%s","priority":"CRITICAL"}
                """.formatted(UUID.randomUUID().toString().substring(0, 8), disasterId, requesterId, locationId), "FIELD_AGENT");
        mockMvc.perform(post("/api/v1/requests/{id}/items", insufficientRequestId)
                .with(user("field-agent").roles("FIELD_AGENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resourceTypeId\":\"%s\",\"quantity\":100}".formatted(resourceTypeId)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/submission", insufficientRequestId)
                .with(user("field-agent").roles("FIELD_AGENT"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/approval", insufficientRequestId)
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/allocations/requests/{id}", insufficientRequestId)
                .with(user("inventory-manager").roles("INVENTORY_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].quantity").value(40));
        mockMvc.perform(get("/api/v1/requests/{id}", insufficientRequestId)
                .with(user("field-agent").roles("FIELD_AGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_FULFILLED"));

        mockMvc.perform(post("/api/v1/disasters/{id}/closure", disasterId)
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"endedAt\":\"2026-01-02T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void authenticationAuthorizationNotFoundAndValidationFailuresReturnExpectedStatuses() throws Exception {
        mockMvc.perform(get("/api/v1/requests")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mockMvc.perform(post("/api/v1/resource-types")
                .with(user("field-agent").roles("FIELD_AGENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"unitOfMeasure\":\"items\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHORIZATION_DENIED"));
        mockMvc.perform(get("/api/v1/disasters/{id}", UUID.randomUUID())
                .with(user("coordinator").roles("DISASTER_COORDINATOR")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(post("/api/v1/disasters")
                .with(user("coordinator").roles("DISASTER_COORDINATOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"FLOOD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private UUID postId(String path, String body, String role) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .with(user(role.toLowerCase()).roles(role))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }
}
