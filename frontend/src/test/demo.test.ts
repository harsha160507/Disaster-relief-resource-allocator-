import { beforeEach, describe, it, expect, vi } from 'vitest';
describe('Explicit sample workspace', () => {
  beforeEach(() => vi.resetModules());
  it('supports server-like sorting search and pagination', async () => {
    const { demoRequest } = await import('../lib/demo');
    const result = await demoRequest(
      '/workspace/inventory?q=water&size=1&sort=quantity&direction=desc',
    );
    expect(result.content).toHaveLength(1);
    expect(result.totalElements).toBe(2);
    expect(result.content[0].resourceName).toBe('Drinking water');
  });
  it('receives stock and protects reserved quantities during adjustments', async () => {
    const { demoRequest } = await import('../lib/demo');
    await demoRequest('/inventory/receipts', {
      method: 'POST',
      body: JSON.stringify({ locationId: 'loc-0', resourceTypeId: 'type-0', quantity: 20 }),
    });
    const row = await demoRequest('/inventory/stock-0');
    expect(row.quantity).toBe(12500);
    await expect(
      demoRequest('/inventory/stock-0', {
        method: 'PATCH',
        body: JSON.stringify({ change: -12000 }),
      }),
    ).rejects.toThrow('reserved');
  });
  it('deducts shipment stock once and records delivery separately', async () => {
    const { demoRequest } = await import('../lib/demo');
    const manifest = await demoRequest('/shipments', {
      method: 'POST',
      body: JSON.stringify({
        destinationId: 'loc-3',
        trackingReference: 'TEST-SHIP',
        allocationIds: ['alloc-1'],
      }),
    });
    const before = await demoRequest('/inventory/stock-1');
    await demoRequest('/shipments/' + manifest.id + '/dispatch', { method: 'POST' });
    const dispatched = await demoRequest('/inventory/stock-1');
    expect(dispatched.quantity).toBe(before.quantity - 40);
    await expect(
      demoRequest('/shipments/' + manifest.id + '/dispatch', { method: 'POST' }),
    ).rejects.toThrow('created');
    await demoRequest('/shipments/' + manifest.id + '/deliver', { method: 'POST' });
    const delivered = await demoRequest('/inventory/stock-1');
    expect(delivered.quantity).toBe(dispatched.quantity);
  });
  it('manually reserves exact quantities and rejects a resource mismatch', async () => {
    const {demoRequest}=await import('../lib/demo');
    const row=await demoRequest('/allocations',{method:'POST',body:JSON.stringify({requestItemId:'item-0',inventoryId:'stock-0',quantity:20})});
    expect(row.quantity).toBe(20);
    const inventory=await demoRequest('/inventory/stock-0');expect(inventory.reservedQuantity).toBe(3220);
    await expect(demoRequest('/allocations',{method:'POST',body:JSON.stringify({requestItemId:'item-0',inventoryId:'stock-1',quantity:1})})).rejects.toThrow('does not match');
  });
  it('releases a cancelled manifest without removing physical stock', async () => {
    const { demoRequest } = await import('../lib/demo');
    const manifest = await demoRequest('/shipments', {
      method: 'POST',
      body: JSON.stringify({
        destinationId: 'loc-0',
        trackingReference: 'CANCEL-SHIP',
        allocationIds: ['alloc-3'],
      }),
    });
    const before = await demoRequest('/inventory/stock-1');
    await demoRequest('/shipments/' + manifest.id + '/cancel', { method: 'POST' });
    const after = await demoRequest('/inventory/stock-1');
    expect(after.quantity).toBe(before.quantity);
    expect(after.reservedQuantity).toBe(before.reservedQuantity - 40);
  });
});
