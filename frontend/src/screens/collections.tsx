import { Box, Droplets, HeartPulse, Tent, MoreHorizontal, ArrowUpRight } from 'lucide-react';
import { Badge } from '../components/ui';
import type { Column } from '../components/DataTable';
import { has, label, number, shortDate, type Profile, type Row, type Screen } from '../model';
export const titles: Record<Screen, string> = {
  dashboard: 'Overview',
  inventory: 'Inventory',
  requests: 'Resource requests',
  allocations: 'Allocations',
  shipments: 'Shipments',
  disasters: 'Disaster response',
  locations: 'Relief locations',
  'resource-types': 'Resource catalogue',
  organizations: 'Organizations',
  users: 'People & access',
  'audit-events': 'Audit history',
};
export const descriptions: Record<Screen, string> = {
  dashboard: 'A clear picture of your resources. A faster path to relief.',
  inventory: 'Track available stock and reservations across your relief locations.',
  requests: 'Follow demand from the first request through confirmed delivery.',
  allocations: 'Match resources to approved demand and prepare them for dispatch.',
  shipments: 'Coordinate dispatch, delivery and every step in between.',
  disasters: 'Keep response teams aligned around the communities that need them.',
  locations: 'Your network of depots, shelters and distribution points.',
  'resource-types': 'A shared catalogue of the resources your teams work with.',
  organizations: 'Manage the organizations participating in relief operations.',
  users: 'Give each person the right access to their workspace.',
  'audit-events': 'A traceable history of important operational changes.',
};
export function ResourceName({ row }: { row: Row }) {
  const name = row.resourceName || row.name || 'Resource';
  const Icon = /water|rehydration/i.test(name)
    ? Droplets
    : /aid|health|hygiene|formula/i.test(name)
      ? HeartPulse
      : /tent|blanket/i.test(name)
        ? Tent
        : Box;
  return (
    <span className="resource-cell">
      <span
        className={
          'resource-icon ' +
          (/water/i.test(name) ? 'water' : /aid|health/i.test(name) ? 'medical' : '')
        }
      >
        <Icon size={20} strokeWidth={1.6} />
      </span>
      <span>
        <strong>{name}</strong>
        <small>
          {row.unitOfMeasure ? label(row.unitOfMeasure) : 'Relief resource'}
          {row.perishable ? ' · Perishable' : ''}
        </small>
      </span>
    </span>
  );
}
export function columns(screen: Screen): Column[] {
  switch (screen) {
    case 'inventory':
      return [
        {
          key: 'name',
          label: 'Resource',
          sortable: true,
          render: (row) => <ResourceName row={row} />,
        },
        {
          key: 'locationName',
          label: 'Location',
          render: (r) => (
            <span className="location-cell">
              <i />
              {r.locationName}
            </span>
          ),
        },
        {
          key: 'quantity',
          label: 'On hand',
          align: 'right',
          sortable: true,
          render: (r) => (
            <>
              <strong>{number(r.quantity)}</strong>
              <small className="unit">{r.unitOfMeasure}</small>
            </>
          ),
        },
        {
          key: 'reservedQuantity',
          label: 'Reserved',
          align: 'right',
          render: (r) => number(r.reservedQuantity),
        },
        {
          key: 'availableQuantity',
          label: 'Available',
          align: 'right',
          sortable: true,
          render: (r) => <strong className="available">{number(r.availableQuantity)}</strong>,
        },
        {
          key: 'status',
          label: 'Status',
          render: (r) => (
            <Badge
              status={
                r.quantity === 0
                  ? 'EMPTY'
                  : r.availableQuantity === 0
                    ? 'FULLY_RESERVED'
                    : r.reservedQuantity > 0
                      ? 'RESERVED'
                      : 'AVAILABLE'
              }
            />
          ),
        },
      ];
    case 'requests':
      return [
        {
          key: 'name',
          label: 'Request',
          sortable: true,
          render: (r) => (
            <span className="record-cell">
              <strong>{r.reference}</strong>
              <small>{r.disasterName || 'Relief request'}</small>
            </span>
          ),
        },
        { key: 'destinationName', label: 'Destination' },
        {
          key: 'priority',
          label: 'Priority',
          render: (r) => (
            <span className={'priority ' + r.priority.toLowerCase()}>
              <i />
              {label(r.priority)}
            </span>
          ),
        },
        {
          key: 'status',
          label: 'Status',
          sortable: true,
          render: (r) => <Badge status={r.status} />,
        },
        { key: 'items', label: 'Resources', align: 'right', render: (r) => r.items?.length || 0 },
      ];
    case 'allocations':
      return [
        {
          key: 'name',
          label: 'Request',
          sortable: true,
          render: (r) => <strong className="reference">{r.requestReference}</strong>,
        },
        { key: 'resourceName', label: 'Resource' },
        { key: 'locationName', label: 'Source location' },
        { key: 'quantity', label: 'Quantity', align: 'right', render: (r) => number(r.quantity) },
        {
          key: 'status',
          label: 'Status',
          sortable: true,
          render: (r) => <Badge status={r.status} />,
        },
      ];
    case 'shipments':
      return [
        {
          key: 'name',
          label: 'Shipment',
          sortable: true,
          render: (r) => (
            <span className="record-cell">
              <strong>{r.trackingReference}</strong>
              <small>{r.id.slice(0, 12)}</small>
            </span>
          ),
        },
        {
          key: 'destinationId',
          label: 'Destination',
          render: (r) => r.destinationName || r.destinationId.slice(0, 10),
        },
        {
          key: 'status',
          label: 'Status',
          sortable: true,
          render: (r) => <Badge status={r.status} />,
        },
        { key: 'dispatchedAt', label: 'Dispatched', render: (r) => shortDate(r.dispatchedAt) },
        { key: 'deliveredAt', label: 'Delivered', render: (r) => shortDate(r.deliveredAt) },
      ];
    case 'disasters':
      return [
        {
          key: 'name',
          label: 'Disaster',
          sortable: true,
          render: (r) => (
            <span className="record-cell">
              <strong>{r.name}</strong>
              <small>{r.description || 'Response operation'}</small>
            </span>
          ),
        },
        { key: 'type', label: 'Type', render: (r) => label(r.type) },
        {
          key: 'status',
          label: 'Status',
          sortable: true,
          render: (r) => <Badge status={r.status} />,
        },
        { key: 'startedAt', label: 'Started', render: (r) => shortDate(r.startedAt) },
        {
          key: 'affectedLocationIds',
          label: 'Affected locations',
          align: 'right',
          render: (r) => r.affectedLocationIds?.length || 0,
        },
      ];
    case 'locations':
      return [
        {
          key: 'name',
          label: 'Location',
          sortable: true,
          render: (r) => <strong>{r.name}</strong>,
        },
        { key: 'address', label: 'Address' },
        { key: 'region', label: 'Region', render: (r) => r.region || '—' },
        {
          key: 'coordinates',
          label: 'Coordinates',
          render: (r) => (r.latitude != null ? r.latitude + ', ' + r.longitude : 'Not recorded'),
        },
      ];
    case 'resource-types':
      return [
        { key: 'name', label: 'Resource', sortable: true, render: (r) => <ResourceName row={r} /> },
        { key: 'unitOfMeasure', label: 'Unit of measure' },
        {
          key: 'perishable',
          label: 'Storage',
          render: (r) => (r.perishable ? 'Perishable' : 'Non-perishable'),
        },
        {
          key: 'active',
          label: 'Status',
          render: (r) => <Badge status={r.active ? 'ACTIVE' : 'INACTIVE'} />,
        },
      ];
    case 'organizations':
      return [
        {
          key: 'name',
          label: 'Organization',
          sortable: true,
          render: (r) => <strong>{r.name}</strong>,
        },
        { key: 'code', label: 'Code' },
        { key: 'contactEmail', label: 'Contact email' },
        {
          key: 'active',
          label: 'Status',
          render: (r) => <Badge status={r.active ? 'ACTIVE' : 'INACTIVE'} />,
        },
      ];
    case 'users':
      return [
        {
          key: 'name',
          label: 'Person',
          sortable: true,
          render: (r) => (
            <span className="record-cell">
              <strong>{r.displayName}</strong>
              <small>{r.email || 'No email recorded'}</small>
            </span>
          ),
        },
        { key: 'roles', label: 'Roles', render: (r) => r.roles.map(label).join(', ') },
        {
          key: 'active',
          label: 'Status',
          render: (r) => <Badge status={r.active ? 'ACTIVE' : 'INACTIVE'} />,
        },
      ];
    case 'audit-events':
      return [
        { key: 'createdAt', label: 'Time', render: (r) => new Date(r.createdAt).toLocaleString() },
        { key: 'operation', label: 'Operation', render: (r) => <strong>{r.operation}</strong> },
        { key: 'entityType', label: 'Record type', render: (r) => label(r.entityType) },
        { key: 'source', label: 'Source', render: (r) => label(r.source) },
        { key: 'action', label: 'Action', render: (r) => label(r.action) },
      ];
    default:
      return [];
  }
}
export function createAction(
  screen: Screen,
  profile: Profile,
): { kind: string; label: string } | null {
  if (screen === 'inventory' && has(profile, 'INVENTORY_MANAGER'))
    return { kind: 'receive', label: 'Receive stock' };
  if (screen === 'requests' && has(profile, 'FIELD_AGENT', 'PARTNER_MANAGER'))
    return { kind: 'requests', label: 'New request' };
  if (screen === 'shipments' && has(profile, 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR'))
    return { kind: 'shipment', label: 'Create shipment' };
  if (screen === 'disasters' && has(profile, 'DISASTER_COORDINATOR'))
    return { kind: 'disasters', label: 'New disaster' };
  if (screen === 'locations' && has(profile, 'PARTNER_MANAGER'))
    return { kind: 'locations', label: 'Add location' };
  if (screen === 'resource-types' && has(profile, 'INVENTORY_MANAGER'))
    return { kind: 'resource-types', label: 'Add resource' };
  if (['users', 'organizations'].includes(screen) && has(profile))
    return { kind: screen, label: screen === 'users' ? 'Add person' : 'Add organization' };
  return null;
}
export const statusOptions: Partial<Record<Screen, string[]>> = {
  inventory: ['AVAILABLE', 'RESERVED', 'EMPTY'],
  requests: [
    'DRAFT',
    'SUBMITTED',
    'APPROVED',
    'PARTIALLY_FULFILLED',
    'FULFILLED',
    'PARTIALLY_DELIVERED',
    'DELIVERED',
    'REJECTED',
  ],
  allocations: ['RESERVED', 'CONFIRMED', 'DISPATCHED', 'DELIVERED', 'CANCELLED'],
  shipments: ['CREATED', 'DISPATCHED', 'DELIVERED', 'CANCELLED'],
  disasters: ['DRAFT', 'ACTIVE', 'CLOSED'],
};
