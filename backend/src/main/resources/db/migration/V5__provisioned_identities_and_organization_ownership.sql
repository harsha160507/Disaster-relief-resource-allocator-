-- Legacy identities have no trustworthy issuer binding. Preserve their data,
-- deactivate them and require explicit reprovisioning rather than guessing identity.
ALTER TABLE user_profiles ADD COLUMN external_issuer varchar(512);
ALTER TABLE user_profiles ADD COLUMN version bigint NOT NULL DEFAULT 0;
UPDATE user_profiles SET active = false WHERE external_issuer IS NULL;
ALTER TABLE user_profiles DROP CONSTRAINT user_profiles_external_subject_key;
ALTER TABLE user_profiles ADD CONSTRAINT uk_user_identity UNIQUE (external_issuer, external_subject);
ALTER TABLE organizations ADD COLUMN version bigint NOT NULL DEFAULT 0;
CREATE UNIQUE INDEX uk_organization_code_ci ON organizations (lower(code)) WHERE code IS NOT NULL;
ALTER TABLE disasters ADD COLUMN organization_id uuid REFERENCES organizations(id);
CREATE INDEX ix_disasters_organization ON disasters(organization_id);
-- Serialize administrative changes across application replicas, including bootstrap
-- and last-active-administrator protection.
CREATE TABLE administration_lock (id integer PRIMARY KEY CHECK (id = 1));
INSERT INTO administration_lock (id) VALUES (1);
