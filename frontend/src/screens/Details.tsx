import { useEffect, useState } from 'react';
import { Plus, Check, Truck, GitBranch, Send, XCircle, CheckCircle2, MapPin } from 'lucide-react';
import { api, send } from '../lib/client';
import { has, number, type Profile, type Row, type Screen } from '../model';
import { Badge, Modal, Notice } from '../components/ui';
import Stepper, { Step } from '../components/supplied/Stepper';
export function Details({
  screen,
  initial,
  profile,
  refresh,
  onClose,
  onForm,
  onChanged,
  theme,
}: {
  screen: Screen;
  initial: Row;
  profile: Profile;
  refresh: number;
  onClose: () => void;
  onForm: (kind: string, row: Row) => void;
  onChanged: (message: string) => void;
  theme: 'light' | 'dark';
}) {
  const [row, setRow] = useState(initial),
    [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [confirmation, setConfirmation] = useState<{
      name: string;
      path: string;
      body?: unknown;
      method?: string;
    } | null>(null);
  useEffect(() => {
    if (!['requests', 'shipments', 'disasters', 'inventory'].includes(screen)) return;
    const controller = new AbortController();
    api('/' + screen + '/' + initial.id, { signal: controller.signal })
      .then((data) => setRow({ ...initial, ...data }))
      .catch((e) => {
        if (e.name !== 'AbortError') setError(e.message);
      });
    return () => controller.abort();
  }, [screen, initial.id, refresh]);
  async function execute() {
    if (!confirmation) return;
    setBusy(true);
    setError('');
    try {
      await send(confirmation.path, confirmation.body, confirmation.method);
      setConfirmation(null);
      onChanged('Operation completed successfully.');
      if (screen === 'allocations') onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const action = (
    name: string,
    path: string,
    Icon: typeof Check,
    body?: unknown,
    method?: string,
  ) => (
    <button className="ui-btn" onClick={() => setConfirmation({ name, path, body, method })}>
      <Icon size={16} />
      {name}
    </button>
  );
  const editable =
    has(profile, 'PARTNER_MANAGER') ||
    (has(profile, 'FIELD_AGENT') && profile.id === row.requesterId);
  return (
    <Modal
      title={
        row.reference ||
        row.trackingReference ||
        row.resourceName ||
        row.name ||
        row.operation ||
        'Record details'
      }
      description={screen.replaceAll('-', ' ')}
      onClose={onClose}
      busy={busy}
      wide
    >
      <div className="detail-body">
        {error && <Notice error>{error}</Notice>}
        <div className="detail-heading">
          {row.status && <Badge status={row.status} />}
          <span className="record-id">{row.id}</span>
        </div>
        {screen === 'requests' && (
          <>
            <Stepper
              step={
                row.status === 'DRAFT'
                  ? 1
                  : row.status === 'SUBMITTED'
                    ? 2
                    : ['DELIVERED', 'PARTIALLY_DELIVERED'].includes(row.status)
                      ? 4
                      : 3
              }
              showFooter={false}
              disableStepIndicators
              frame={false}
              theme={theme}
              accentColor="var(--accent)"
              width="100%"
            >
              <Step title="Draft">
                <p>Build the resource list, then submit this request for review.</p>
              </Step>
              <Step title="Review">
                <p>A coordinator will review and approve this request.</p>
              </Step>
              <Step title="Allocation">
                <p>Allocated quantities include reserved, in-transit and delivered goods.</p>
              </Step>
              <Step title="Delivery">
                <p>Only confirmed deliveries count toward delivered quantities.</p>
              </Step>
            </Stepper>
            <div className="detail-summary">
              <div>
                <small>Destination</small>
                <strong>{row.destinationName || row.destinationId}</strong>
              </div>
              <div>
                <small>Priority</small>
                <strong>{row.priority}</strong>
              </div>
            </div>
            <table className="detail-table">
              <thead>
                <tr>
                  <th>Resource</th>
                  <th>Requested</th>
                  <th>Allocated</th>
                  <th>Delivered</th>
                </tr>
              </thead>
              <tbody>
                {row.items?.map((item: Row) => (
                  <tr key={item.id}>
                    <td>{item.resourceName || item.resourceTypeId}</td>
                    <td>{number(item.requestedQuantity)}</td>
                    <td>{number(item.allocatedQuantity)}</td>
                    <td>{number(item.deliveredQuantity)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {!row.items?.length && <p className="muted">No resources added yet.</p>}
            <div className="detail-actions">
              {['APPROVED', 'PARTIALLY_FULFILLED', 'PARTIALLY_DELIVERED'].includes(row.status) &&
                has(profile, 'INVENTORY_MANAGER') && (
                  <button className="ui-btn" onClick={() => onForm('reserve', row)}>
                    <GitBranch size={16} />
                    Reserve exact quantity
                  </button>
                )}
              {row.status === 'DRAFT' && editable && (
                <>
                  <button className="ui-btn" onClick={() => onForm('request-item', row)}>
                    <Plus size={16} />
                    Add resource
                  </button>
                  {action('Submit for review', '/requests/' + row.id + '/submission', Send)}
                </>
              )}
              {row.status === 'SUBMITTED' && has(profile, 'DISASTER_COORDINATOR') && (
                <>
                  {action('Approve request', '/requests/' + row.id + '/approval', Check)}
                  {action('Reject request', '/requests/' + row.id + '/rejection', XCircle)}
                </>
              )}
              {['APPROVED', 'PARTIALLY_FULFILLED', 'PARTIALLY_DELIVERED'].includes(row.status) &&
                has(profile, 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR') &&
                action('Allocate available stock', '/allocations/requests/' + row.id, GitBranch)}
            </div>
            {row.notes && (
              <div className="detail-notes">
                <small>Notes</small>
                <p>{row.notes}</p>
              </div>
            )}
          </>
        )}
        {screen === 'shipments' && (
          <>
            <div className="detail-summary">
              <div>
                <small>Destination</small>
                <strong>{row.destinationName || row.destinationId}</strong>
              </div>
              <div>
                <small>Dispatched</small>
                <strong>
                  {row.dispatchedAt
                    ? new Date(row.dispatchedAt).toLocaleString()
                    : 'Awaiting dispatch'}
                </strong>
              </div>
              <div>
                <small>Delivered</small>
                <strong>
                  {row.deliveredAt ? new Date(row.deliveredAt).toLocaleString() : 'Not delivered'}
                </strong>
              </div>
            </div>
            <table className="detail-table">
              <thead>
                <tr>
                  <th>Allocation</th>
                  <th>Quantity</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {row.allocations?.map((a: Row) => (
                  <tr key={a.id}>
                    <td>{a.resourceName || a.id}</td>
                    <td>{number(a.quantity)}</td>
                    <td>
                      <Badge status={a.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div className="detail-actions">
              {row.status === 'CREATED' &&
                has(profile, 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR') && (
                  <>
                    {action('Dispatch shipment', '/shipments/' + row.id + '/dispatch', Truck)}
                    {action('Cancel shipment', '/shipments/' + row.id + '/cancel', XCircle)}
                  </>
                )}
              {row.status === 'DISPATCHED' &&
                has(profile, 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR', 'FIELD_AGENT') &&
                action('Confirm delivery', '/shipments/' + row.id + '/deliver', CheckCircle2)}
            </div>
            <Notice>
              Stock is deducted at dispatch. Delivery confirmation records receipt without deducting
              stock again.
            </Notice>
          </>
        )}
        {screen === 'disasters' && (
          <>
            <p>{row.description || 'No description recorded.'}</p>
            <div className="detail-summary">
              <div>
                <small>Disaster type</small>
                <strong>{row.type}</strong>
              </div>
              <div>
                <small>Affected locations</small>
                <strong>{row.affectedLocationIds?.length || 0}</strong>
              </div>
            </div>
            <div className="detail-actions">
              {has(profile, 'DISASTER_COORDINATOR') && (
                <>
                  {row.status === 'DRAFT' &&
                    action('Activate response', '/disasters/' + row.id + '/activation', Check)}
                  {row.status === 'ACTIVE' && (
                    <>
                      <button className="ui-btn" onClick={() => onForm('affected-location', row)}>
                        <MapPin size={16} />
                        Add affected location
                      </button>
                      {action('Close response', '/disasters/' + row.id + '/closure', CheckCircle2, {
                        endedAt: new Date().toISOString(),
                      })}
                    </>
                  )}
                </>
              )}
            </div>
          </>
        )}
        {screen === 'inventory' && (
          <>
            <div className="detail-summary">
              <div>
                <small>On hand</small>
                <strong>
                  {number(row.quantity)} {row.unitOfMeasure}
                </strong>
              </div>
              <div>
                <small>Reserved</small>
                <strong>{number(row.reservedQuantity)}</strong>
              </div>
              <div>
                <small>Available</small>
                <strong className="available">{number(row.availableQuantity)}</strong>
              </div>
            </div>
            <button className="ui-btn" onClick={() => onForm('adjust', row)}>
              Adjust quantity
            </button>
          </>
        )}
        {['organizations', 'users'].includes(screen) && (
          <>
            <dl className="record-fields">
              {Object.entries(row)
                .filter(([key]) => !['id', 'roles', 'items', 'allocations'].includes(key))
                .map(([key, value]) => (
                  <div key={key}>
                    <dt>{key}</dt>
                    <dd>{String(value ?? '—')}</dd>
                  </div>
                ))}
            </dl>
            {has(profile) && (
              <button
                className="ui-btn"
                onClick={() => onForm(screen === 'users' ? 'edit-user' : 'edit-organization', row)}
              >
                Edit {screen === 'users' ? 'person' : 'organization'}
              </button>
            )}
          </>
        )}
        {!['requests', 'shipments', 'disasters', 'inventory', 'users', 'organizations'].includes(
          screen,
        ) && (
          <dl className="record-fields">
            {Object.entries(row).map(([key, value]) => (
              <div key={key}>
                <dt>{key}</dt>
                <dd>{typeof value === 'object' ? JSON.stringify(value) : String(value ?? '—')}</dd>
              </div>
            ))}
          </dl>
        )}
        {confirmation && (
          <div className="confirmation" role="alert">
            <h3>{confirmation.name}?</h3>
            <p>
              {confirmation.name.includes('Dispatch')
                ? 'This moves the full manifest out of warehouse stock.'
                : confirmation.name.includes('Cancel')
                  ? 'This releases the pending reservations. A new allocation will be needed to ship these resources.'
                  : 'Confirm this operation for the selected record.'}
            </p>
            <div>
              <button className="ui-btn" disabled={busy} onClick={() => setConfirmation(null)}>
                Go back
              </button>
              <button className="ui-btn ui-btn-primary" disabled={busy} onClick={execute}>
                {busy ? 'Working…' : 'Confirm'}
              </button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
