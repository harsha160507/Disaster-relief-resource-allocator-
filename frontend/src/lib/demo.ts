import type { Row, Role } from '../model';
const uid = () => crypto.randomUUID();
const date = () => new Date().toISOString();
export const demoProfile = {
  id: 'demo-user',
  displayName: 'Alex Morgan',
  email: 'alex@relief.example',
  organizationId: 'org-1',
  roles: ['INVENTORY_MANAGER'] as Role[],
};
const names = [
  'Central relief depot',
  'North distribution hub',
  'Riverside shelter',
  'East field station',
  'Community health centre',
];
const locations: Row[] = names.map((name, i) => ({
  id: 'loc-' + i,
  name,
  address: [
    '18 Logistics Way',
    '42 North Road',
    '6 Riverbank Lane',
    '12 East Avenue',
    '3 Health Road',
  ][i],
  organizationId: 'org-1',
}));
const catalog = [
  ['Drinking water', 'litres'],
  ['Rice & staple food', 'kg'],
  ['First aid kits', 'kits'],
  ['Thermal blankets', 'units'],
  ['Oral rehydration salts', 'packs'],
  ['Emergency tents', 'units'],
  ['Portable generators', 'units'],
  ['Water purification tablets', 'packs'],
  ['Infant formula', 'tins'],
  ['Hygiene kits', 'kits'],
];
const types: Row[] = catalog.map(([name, unitOfMeasure], i) => ({
  id: 'type-' + i,
  name,
  unitOfMeasure,
  perishable: [0, 1, 4, 8].includes(i),
  active: true,
}));
const physical = [12480, 8200, 640, 1240, 4800, 148, 18, 1600, 320, 280];
const reserved = [3200, 1600, 180, 400, 1200, 24, 6, 0, 0, 280];
const stock: Row[] = types.map((type, i) => ({
  id: 'stock-' + i,
  locationId: locations[i % 3].id,
  locationName: locations[i % 3].name,
  resourceTypeId: type.id,
  resourceName: type.name,
  unitOfMeasure: type.unitOfMeasure,
  quantity: physical[i],
  reservedQuantity: reserved[i],
  availableQuantity: physical[i] - reserved[i],
  perishable: type.perishable,
}));
const disasters: Row[] = [
  {
    id: 'dis-1',
    name: 'River basin flood response',
    type: 'FLOOD',
    status: 'ACTIVE',
    organizationId: 'org-1',
    description: 'Coordinated relief across affected communities.',
    startedAt: date(),
    affectedLocationIds: locations.map((l) => l.id),
  },
  {
    id: 'dis-2',
    name: 'Coastal storm recovery',
    type: 'HURRICANE',
    status: 'ACTIVE',
    organizationId: 'org-1',
    description: 'Essential supplies and temporary shelter.',
    startedAt: date(),
    affectedLocationIds: locations.map((l) => l.id),
  },
];
const requestStatuses = [
  'APPROVED',
  'PARTIALLY_FULFILLED',
  'SUBMITTED',
  'FULFILLED',
  'APPROVED',
  'PARTIALLY_DELIVERED',
  'DRAFT',
  'DELIVERED',
];
const requests: Row[] = requestStatuses.map((status, i) => ({
  id: 'req-' + i,
  reference: 'REQ-2026-' + String(1048 - i),
  disasterId: disasters[i % 2].id,
  disasterName: disasters[i % 2].name,
  destinationId: locations[(i + 2) % 5].id,
  destinationName: locations[(i + 2) % 5].name,
  requesterId: 'demo-user',
  requesterName: 'Field operations',
  status,
  priority: ['CRITICAL', 'HIGH', 'HIGH', 'NORMAL', 'CRITICAL', 'HIGH', 'NORMAL', 'NORMAL'][i],
  notes: 'Deliver to the designated relief coordinator.',
  items: [
    {
      id: 'item-' + i,
      resourceTypeId: types[i % 6].id,
      resourceName: types[i % 6].name,
      unitOfMeasure: types[i % 6].unitOfMeasure,
      requestedQuantity: 100,
      allocatedQuantity: ['FULFILLED', 'DELIVERED', 'PARTIALLY_DELIVERED'].includes(status)
        ? 100
        : status === 'PARTIALLY_FULFILLED'
          ? 40
          : 0,
      deliveredQuantity: status === 'DELIVERED' ? 100 : status === 'PARTIALLY_DELIVERED' ? 40 : 0,
    },
  ],
}));
const allocationRows: Row[] = [1, 3, 5].map((i) => ({
  id: 'alloc-' + i,
  requestItemId: 'item-' + i,
  requestId: 'req-' + i,
  requestReference: requests[i].reference,
  destinationId: requests[i].destinationId,
  destinationName: requests[i].destinationName,
  inventoryId: 'stock-' + (i % 6),
  resourceName: types[i % 6].name,
  locationName: stock[i % 6].locationName,
  quantity: i === 1 ? 40 : 60,
  status: 'RESERVED',
  shipmentId: null,
}));
const shipments: Row[] = [
  {
    id: 'ship-1',
    trackingReference: 'SHP-2026-0086',
    destinationId: 'loc-2',
    status: 'DISPATCHED',
    dispatchedAt: date(),
    version: 1,
    allocations: [],
  },
  {
    id: 'ship-2',
    trackingReference: 'SHP-2026-0085',
    destinationId: 'loc-3',
    status: 'CREATED',
    version: 0,
    allocations: [],
  },
  {
    id: 'ship-3',
    trackingReference: 'SHP-2026-0084',
    destinationId: 'loc-4',
    status: 'DELIVERED',
    dispatchedAt: date(),
    deliveredAt: date(),
    version: 2,
    allocations: [],
  },
];
const organizations: Row[] = [
  {
    id: 'org-1',
    name: 'Relief Operations Network',
    code: 'RON',
    contactEmail: 'operations@relief.example',
    active: true,
    version: 0,
  },
];
const users: Row[] = [
  {
    ...demoProfile,
    issuer: 'https://identity.example',
    subject: 'alex-demo',
    active: true,
    version: 0,
  },
];
const audit: Row[] = [];
const db: Record<string, Row[]> = {
  inventory: stock,
  'resource-types': types,
  locations,
  disasters,
  requests,
  allocations: allocationRows,
  shipments,
  organizations,
  users,
  'audit-events': audit,
};
function record(operation: string, entityId: string, entityType: string) {
  audit.unshift({
    id: uid(),
    createdAt: date(),
    operation,
    entityId,
    entityType,
    action: 'UPDATED',
    source: 'USER',
    actorId: 'demo-user',
    details: '{}',
  });
}
const fail = (message: string): never => {
  throw new Error(message);
};
function progress(request: Row) {
  const items = request.items as Row[];
  request.status = items.every((i) => i.deliveredQuantity >= i.requestedQuantity)
    ? 'DELIVERED'
    : items.some((i) => i.deliveredQuantity > 0)
      ? 'PARTIALLY_DELIVERED'
      : items.every((i) => i.allocatedQuantity >= i.requestedQuantity)
        ? 'FULFILLED'
        : items.some((i) => i.allocatedQuantity > 0)
          ? 'PARTIALLY_FULFILLED'
          : 'APPROVED';
}
function allocate(request: Row) {
  if (!['APPROVED', 'PARTIALLY_FULFILLED', 'PARTIALLY_DELIVERED'].includes(request.status))
    fail('Only approved requests with unmet demand can be allocated.');
  const result: Row[] = [];
  for (const item of request.items)
    for (const inventory of stock.filter((s) => s.resourceTypeId === item.resourceTypeId)) {
      const quantity = Math.min(
        item.requestedQuantity - item.allocatedQuantity,
        inventory.quantity - inventory.reservedQuantity,
      );
      if (quantity <= 0) continue;
      inventory.reservedQuantity += quantity;
      inventory.availableQuantity = inventory.quantity - inventory.reservedQuantity;
      item.allocatedQuantity += quantity;
      const row = {
        id: uid(),
        requestId: request.id,
        requestReference: request.reference,
        requestItemId: item.id,
        destinationId: request.destinationId,
        destinationName: request.destinationName,
        inventoryId: inventory.id,
        resourceName: inventory.resourceName,
        locationName: inventory.locationName,
        quantity,
        status: 'RESERVED',
        shipmentId: null,
      };
      allocationRows.unshift(row);
      result.push(row);
    }
  progress(request);
  return result;
}
export async function demoRequest(path: string, init: RequestInit = {}) {
  await new Promise((resolve) => setTimeout(resolve, 180));
  if (init.signal?.aborted) throw new DOMException('Aborted', 'AbortError');
  const u = new URL(path, 'https://demo.invalid');
  const parts = u.pathname.split('/').filter(Boolean);
  const method = init.method || 'GET';
  const body = init.body ? JSON.parse(String(init.body)) : {};
  if (method === 'GET') {
    if (parts[0] === 'me') return parts[1] === 'organization' ? organizations[0] : demoProfile;
    if (parts[1] === 'overview')
      return {
        inventoryLines: stock.length,
        locations: locations.length,
        openRequests: requests.filter((r) => !['DRAFT', 'DELIVERED', 'REJECTED'].includes(r.status))
          .length,
        inTransit: shipments.filter((s) => s.status === 'DISPATCHED').length,
        delivered: shipments.filter((s) => s.status === 'DELIVERED').length,
      };
    const collection = parts[0] === 'workspace' || parts[0] === 'admin' ? parts[1] : parts[0];
    if (parts[0] !== 'workspace' && parts[0] !== 'admin' && parts[1])
      return structuredClone(
        db[collection]?.find((r) => r.id === parts[1]) || fail('Record not found.'),
      );
    let rows = [...(db[collection] || [])];
    const q = (u.searchParams.get('q') || '').toLowerCase();
    const status = u.searchParams.get('status');
    const location = u.searchParams.get('locationId');
    if (q)
      rows = rows.filter((row) =>
        [
          row.name,
          row.resourceName,
          row.reference,
          row.trackingReference,
          row.requestReference,
          row.displayName,
          row.operation,
        ].some((v) =>
          String(v || '')
            .toLowerCase()
            .includes(q),
        ),
      );
    if (location)
      rows = rows.filter(
        (r) => (collection === 'allocations' ? r.destinationId : r.locationId) === location,
      );
    if (status)
      rows = rows.filter((r) =>
        collection === 'inventory'
          ? status === 'EMPTY'
            ? r.quantity === 0
            : status === 'RESERVED'
              ? r.reservedQuantity > 0
              : r.availableQuantity > 0
          : r.status === status,
      );
    const sort = u.searchParams.get('sort'),
      direction = u.searchParams.get('direction') === 'asc' ? 1 : -1;
    if (sort && sort !== 'createdAt') {
      const get = (r: Row) =>
        sort === 'name'
          ? r.name ||
            r.resourceName ||
            r.reference ||
            r.trackingReference ||
            r.requestReference ||
            r.displayName
          : r[sort];
      rows.sort((a, b) =>
        typeof get(a) === 'number'
          ? (get(a) - get(b)) * direction
          : String(get(a)).localeCompare(String(get(b))) * direction,
      );
    }
    const page = Number(u.searchParams.get('page') || 0),
      size = Number(u.searchParams.get('size') || 20),
      totalElements = rows.length;
    return structuredClone({
      content: rows.slice(page * size, (page + 1) * size),
      items: rows.slice(page * size, (page + 1) * size),
      page,
      size,
      totalElements,
      totalPages: Math.ceil(totalElements / size),
    });
  }
  if (parts[0] === 'inventory') {
    if (parts[1] === 'receipts') {
      const type = types.find((r) => r.id === body.resourceTypeId) || fail('Select a resource.');
      const loc = locations.find((r) => r.id === body.locationId) || fail('Select a location.');
      let row = stock.find((r) => r.resourceTypeId === type.id && r.locationId === loc.id);
      if (!row) {
        row = {
          id: uid(),
          locationId: loc.id,
          locationName: loc.name,
          resourceTypeId: type.id,
          resourceName: type.name,
          unitOfMeasure: type.unitOfMeasure,
          quantity: 0,
          reservedQuantity: 0,
          availableQuantity: 0,
          perishable: type.perishable,
        };
        stock.unshift(row);
      }
      row.quantity += Number(body.quantity);
      row.availableQuantity = row.quantity - row.reservedQuantity;
      record('inventory.received', row.id, 'INVENTORY');
      return structuredClone(row);
    }
    const row = stock.find((r) => r.id === parts[1]) || fail('Inventory not found.');
    if (row.quantity + Number(body.change) < row.reservedQuantity)
      fail('Stock cannot fall below its reserved quantity.');
    row.quantity += Number(body.change);
    row.availableQuantity = row.quantity - row.reservedQuantity;
    record('inventory.adjusted', row.id, 'INVENTORY');
    return structuredClone(row);
  }
  if (parts[0] === 'allocations' && parts[1] === 'requests') {
    const result = allocate(requests.find((r) => r.id === parts[2]) || fail('Request not found.'));
    record('allocation.reserved', parts[2], 'RESOURCE_REQUEST');
    return structuredClone(result);
  }
  if (parts[0] === 'shipments') {
    if (parts.length === 1) {
      const selected = body.allocationIds.map(
        (id: string) => allocationRows.find((a) => a.id === id) || fail('Allocation not found.'),
      );
      if (
        !selected.length ||
        selected.some(
          (a: Row) =>
            a.status !== 'RESERVED' || a.shipmentId || a.destinationId !== body.destinationId,
        )
      )
        fail('Choose unassigned allocations for one destination.');
      if (shipments.some((s) => s.trackingReference === body.trackingReference))
        fail('Tracking reference already exists.');
      const row = { id: uid(), ...body, status: 'CREATED', version: 0, allocations: selected };
      selected.forEach((a: Row) => {
        a.status = 'CONFIRMED';
        a.shipmentId = row.id;
      });
      shipments.unshift(row);
      record('shipment.created', row.id, 'SHIPMENT');
      return structuredClone(row);
    }
    const row = shipments.find((s) => s.id === parts[1]) || fail('Shipment not found.'),
      action = parts[2];
    const expected = action === 'deliver' ? 'DISPATCHED' : 'CREATED';
    if (row.status !== expected) fail('Shipment must be ' + expected.toLowerCase() + '.');
    for (const allocation of row.allocations) {
      const inv = stock.find((s) => s.id === allocation.inventoryId)!;
      const req = requests.find((r) => r.id === allocation.requestId)!;
      const item = req.items.find((i: Row) => i.id === allocation.requestItemId);
      if (action === 'dispatch') {
        inv.quantity -= allocation.quantity;
        inv.reservedQuantity -= allocation.quantity;
        allocation.status = 'DISPATCHED';
      }
      if (action === 'cancel') {
        inv.reservedQuantity -= allocation.quantity;
        item.allocatedQuantity -= allocation.quantity;
        allocation.status = 'CANCELLED';
      }
      if (action === 'deliver') {
        item.deliveredQuantity += allocation.quantity;
        allocation.status = 'DELIVERED';
      }
      inv.availableQuantity = inv.quantity - inv.reservedQuantity;
      progress(req);
    }
    row.status =
      action === 'dispatch' ? 'DISPATCHED' : action === 'deliver' ? 'DELIVERED' : 'CANCELLED';
    if (action === 'dispatch') row.dispatchedAt = date();
    if (action === 'deliver') row.deliveredAt = date();
    row.version++;
    record('shipment.status_changed', row.id, 'SHIPMENT');
    return structuredClone(row);
  }
  if (parts[0] === 'requests' && parts[1]) {
    const row = requests.find((r) => r.id === parts[1]) || fail('Request not found.');
    if (parts[2] === 'items') {
      row.items.push({
        id: uid(),
        ...body,
        requestedQuantity: Number(body.quantity),
        allocatedQuantity: 0,
        deliveredQuantity: 0,
      });
    } else {
      const map: Row = {
        submission: ['DRAFT', 'SUBMITTED'],
        approval: ['SUBMITTED', 'APPROVED'],
        rejection: ['SUBMITTED', 'REJECTED'],
      };
      const transition = map[parts[2]] || fail('Unknown action.');
      if (row.status !== transition[0]) fail('Invalid request transition.');
      if (parts[2] === 'submission' && !row.items.length) fail('Add a resource item first.');
      row.status = transition[1];
    }
    record('request.updated', row.id, 'RESOURCE_REQUEST');
    return structuredClone(row);
  }
  if (parts[0] === 'disasters' && parts[1]) {
    const row = disasters.find((d) => d.id === parts[1]) || fail('Disaster not found.');
    if (parts[2] === 'activation') row.status = 'ACTIVE';
    else if (parts[2] === 'closure') {
      row.status = 'CLOSED';
      row.endedAt = date();
    } else if (parts[2] === 'affected-locations') row.affectedLocationIds.push(parts[3]);
    return structuredClone(row);
  }
  const collection = parts[0] === 'admin' ? parts[1] : parts[0];
  const target = db[collection] || fail('This demo operation is unavailable.');
  if (method === 'PUT') {
    const row = target.find((r) => r.id === parts[2]) || fail('Record not found.');
    Object.assign(row, body, { version: row.version + 1 });
    return structuredClone(row);
  }
  const row: Row = { id: uid(), ...body, version: 0 };
  if (collection === 'requests') {
    Object.assign(row, {
      status: 'DRAFT',
      items: [],
      destinationName: locations.find((l) => l.id === body.destinationId)?.name,
      disasterName: disasters.find((d) => d.id === body.disasterId)?.name,
    });
  }
  if (collection === 'resource-types') row.active = true;
  if (collection === 'disasters') {
    row.status = 'DRAFT';
    row.affectedLocationIds = [];
  }
  if (['organizations', 'users'].includes(collection)) row.active = true;
  target.unshift(row);
  record(collection + '.created', row.id, collection.toUpperCase());
  return structuredClone(row);
}
