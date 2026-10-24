-- Keep invalid stock/request quantities out even when data is written outside JPA.
ALTER TABLE inventory ADD CONSTRAINT ck_inventory_quantities
    CHECK (quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= quantity);
ALTER TABLE resource_request_items ADD CONSTRAINT ck_request_item_quantities
    CHECK (requested_quantity >= 0.001 AND allocated_quantity >= 0
        AND allocated_quantity <= requested_quantity);
ALTER TABLE allocations ADD CONSTRAINT ck_allocation_quantity CHECK (quantity >= 0.001);
ALTER TABLE locations ADD CONSTRAINT ck_location_coordinates
    CHECK ((latitude IS NULL OR latitude BETWEEN -90 AND 90)
        AND (longitude IS NULL OR longitude BETWEEN -180 AND 180));

CREATE INDEX ix_user_profiles_organization ON user_profiles(organization_id);
CREATE INDEX ix_locations_organization ON locations(organization_id);
CREATE INDEX ix_disasters_status ON disasters(status);
CREATE INDEX ix_disaster_locations_location ON disaster_locations(location_id);
CREATE INDEX ix_requests_disaster_status ON resource_requests(disaster_id, status);
CREATE INDEX ix_requests_requester ON resource_requests(requested_by_id);
CREATE INDEX ix_requests_destination ON resource_requests(destination_location_id);
CREATE INDEX ix_requests_status ON resource_requests(status);
CREATE INDEX ix_request_items_request_created ON resource_request_items(request_id, created_at);
CREATE INDEX ix_request_items_resource_type ON resource_request_items(resource_type_id);
CREATE INDEX ix_inventory_resource_type ON inventory(resource_type_id);
CREATE INDEX ix_shipments_destination ON shipments(destination_location_id);
CREATE INDEX ix_allocations_request_item ON allocations(request_item_id);
CREATE INDEX ix_allocations_inventory ON allocations(inventory_id);
CREATE INDEX ix_allocations_shipment ON allocations(shipment_id);
CREATE INDEX ix_audit_entity_created ON audit_events(entity_type, entity_id, created_at);
CREATE INDEX ix_audit_actor ON audit_events(actor_id);
