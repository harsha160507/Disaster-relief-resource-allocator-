CREATE TABLE inventory (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    location_id uuid NOT NULL REFERENCES locations(id),
    resource_type_id uuid NOT NULL REFERENCES resource_types(id),
    quantity numeric(19,3) NOT NULL,
    reserved_quantity numeric(19,3) NOT NULL,
    version bigint NOT NULL,
    CONSTRAINT uk_inventory_location_resource UNIQUE (location_id, resource_type_id)
);

-- Schema for existing entities; shipment/audit application workflows are later phases.
CREATE TABLE shipments (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    destination_location_id uuid NOT NULL REFERENCES locations(id),
    status varchar(20) NOT NULL CHECK (status IN (
        'CREATED', 'DISPATCHED', 'DELIVERED', 'FAILED', 'CANCELLED')),
    tracking_reference varchar(255) UNIQUE,
    dispatched_at timestamptz(6),
    delivered_at timestamptz(6)
);

CREATE TABLE allocations (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    request_item_id uuid NOT NULL REFERENCES resource_request_items(id),
    inventory_id uuid NOT NULL REFERENCES inventory(id),
    quantity numeric(19,3) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN (
        'RESERVED', 'CONFIRMED', 'DISPATCHED', 'DELIVERED', 'CANCELLED')),
    shipment_id uuid REFERENCES shipments(id)
);

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    action varchar(20) NOT NULL CHECK (action IN (
        'CREATED', 'UPDATED', 'STATUS_CHANGED', 'DELETED')),
    entity_type varchar(80) NOT NULL,
    entity_id uuid NOT NULL,
    actor_id uuid REFERENCES user_profiles(id),
    details varchar(4000)
);
