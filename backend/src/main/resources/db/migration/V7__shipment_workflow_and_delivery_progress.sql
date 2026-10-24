ALTER TABLE shipments ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE allocations ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE resource_requests ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE resource_request_items ADD COLUMN delivered_quantity numeric(19,3) NOT NULL DEFAULT 0;

-- Preserve trustworthy pre-existing delivered allocations; reject inconsistent totals.
UPDATE resource_request_items item SET delivered_quantity = COALESCE(
    (SELECT sum(a.quantity) FROM allocations a WHERE a.request_item_id = item.id AND a.status = 'DELIVERED'), 0);
ALTER TABLE resource_request_items ADD CONSTRAINT ck_item_delivery_progress
    CHECK (delivered_quantity >= 0 AND delivered_quantity <= allocated_quantity);

ALTER TABLE resource_requests DROP CONSTRAINT resource_requests_status_check;
ALTER TABLE resource_requests ADD CONSTRAINT resource_requests_status_check CHECK (status IN (
    'DRAFT', 'SUBMITTED', 'APPROVED', 'PARTIALLY_FULFILLED', 'FULFILLED',
    'PARTIALLY_DELIVERED', 'DELIVERED', 'REJECTED', 'CANCELLED', 'CLOSED'));
UPDATE resource_requests request SET status = CASE
    WHEN NOT EXISTS (SELECT 1 FROM resource_request_items item WHERE item.request_id = request.id
        AND item.delivered_quantity < item.requested_quantity) THEN 'DELIVERED'
    ELSE 'PARTIALLY_DELIVERED' END
WHERE status IN ('APPROVED', 'PARTIALLY_FULFILLED', 'FULFILLED')
    AND EXISTS (SELECT 1 FROM resource_request_items item WHERE item.request_id = request.id
        AND item.delivered_quantity > 0);

-- Existing legacy rows are retained; new writes must obey the workflow invariants.
ALTER TABLE shipments ADD CONSTRAINT ck_shipment_times CHECK (
    (delivered_at IS NULL OR (dispatched_at IS NOT NULL AND delivered_at >= dispatched_at))
    AND (status <> 'DISPATCHED' OR dispatched_at IS NOT NULL)
    AND (status <> 'DELIVERED' OR (dispatched_at IS NOT NULL AND delivered_at IS NOT NULL))
    AND (status NOT IN ('CREATED', 'CANCELLED') OR (dispatched_at IS NULL AND delivered_at IS NULL))
) NOT VALID;
ALTER TABLE allocations ADD CONSTRAINT ck_allocation_shipment_link CHECK (
    status NOT IN ('CONFIRMED', 'DISPATCHED', 'DELIVERED') OR shipment_id IS NOT NULL
) NOT VALID;
CREATE INDEX ix_shipments_created_id ON shipments(created_at DESC, id DESC);
