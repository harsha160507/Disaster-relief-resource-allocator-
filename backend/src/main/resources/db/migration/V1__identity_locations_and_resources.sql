-- Initial identity, location and resource catalog schema.
-- UUIDs and timestamps are supplied by the JPA application.
CREATE TABLE organizations (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    name varchar(160) NOT NULL,
    code varchar(80),
    contact_email varchar(255),
    active boolean NOT NULL
);

CREATE TABLE user_profiles (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    external_subject varchar(255) NOT NULL UNIQUE,
    display_name varchar(120) NOT NULL,
    email varchar(255),
    active boolean NOT NULL,
    organization_id uuid REFERENCES organizations(id)
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES user_profiles(id),
    role varchar(40) NOT NULL,
    PRIMARY KEY (user_id, role),
    CONSTRAINT ck_user_role CHECK (role IN (
        'SYSTEM_ADMIN', 'DISASTER_COORDINATOR', 'INVENTORY_MANAGER',
        'FIELD_AGENT', 'PARTNER_MANAGER', 'VIEWER'))
);

CREATE TABLE locations (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    name varchar(160) NOT NULL,
    address varchar(255) NOT NULL,
    region varchar(100),
    latitude numeric(9,6),
    longitude numeric(9,6),
    organization_id uuid NOT NULL REFERENCES organizations(id)
);

CREATE TABLE resource_types (
    id uuid PRIMARY KEY,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    name varchar(120) NOT NULL UNIQUE,
    unit_of_measure varchar(30) NOT NULL,
    perishable boolean NOT NULL,
    active boolean NOT NULL
);
