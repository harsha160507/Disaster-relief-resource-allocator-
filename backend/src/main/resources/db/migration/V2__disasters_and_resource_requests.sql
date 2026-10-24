CREATE TABLE disasters (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    name varchar(160) NOT NULL,
    type varchar(40) NOT NULL CHECK (type IN (
        'EARTHQUAKE', 'FLOOD', 'HURRICANE', 'WILDFIRE',
        'TORNADO', 'DROUGHT', 'EPIDEMIC', 'OTHER')),
    status varchar(20) NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    description varchar(2000),
    started_at timestamptz(6) NOT NULL,
    ended_at timestamptz(6)
);

CREATE TABLE disaster_locations (
    disaster_id uuid NOT NULL REFERENCES disasters(id),
    location_id uuid NOT NULL REFERENCES locations(id),
    PRIMARY KEY (disaster_id, location_id)
);

CREATE TABLE resource_requests (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    reference varchar(40) NOT NULL UNIQUE,
    disaster_id uuid NOT NULL REFERENCES disasters(id),
    requested_by_id uuid NOT NULL REFERENCES user_profiles(id),
    destination_location_id uuid NOT NULL REFERENCES locations(id),
    status varchar(20) NOT NULL CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_FULFILLED',
        'FULFILLED', 'REJECTED', 'CANCELLED', 'CLOSED')),
    priority varchar(20) NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')),
    notes varchar(2000)
);

CREATE TABLE resource_request_items (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    request_id uuid NOT NULL REFERENCES resource_requests(id),
    resource_type_id uuid NOT NULL REFERENCES resource_types(id),
    requested_quantity numeric(19,3) NOT NULL,
    allocated_quantity numeric(19,3) NOT NULL,
    version bigint NOT NULL
);
