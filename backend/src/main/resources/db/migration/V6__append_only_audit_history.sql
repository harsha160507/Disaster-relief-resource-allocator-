-- Preserve pre-existing events and identify their unknown historical attribution.
ALTER TABLE audit_events
    ADD COLUMN organization_id uuid REFERENCES organizations(id),
    ADD COLUMN actor_organization_id uuid REFERENCES organizations(id),
    ADD COLUMN actor_roles varchar(256),
    ADD COLUMN source varchar(24) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN operation varchar(80) NOT NULL DEFAULT 'legacy',
    ADD COLUMN correlation_id uuid,
    ADD CONSTRAINT ck_audit_source CHECK (source IN ('USER', 'SYSTEM_INTERNAL', 'BOOTSTRAP', 'LEGACY')),
    ADD CONSTRAINT ck_audit_user_actor CHECK (source <> 'USER' OR actor_id IS NOT NULL);

CREATE INDEX ix_audit_created_id ON audit_events (created_at DESC, id DESC);
CREATE INDEX ix_audit_organization_created ON audit_events (organization_id, created_at DESC);
CREATE INDEX ix_audit_correlation ON audit_events (correlation_id);

CREATE FUNCTION reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit events are append-only' USING ERRCODE = '55000';
END;
$$;

CREATE TRIGGER audit_no_update_delete BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
CREATE TRIGGER audit_no_truncate BEFORE TRUNCATE ON audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_mutation();
