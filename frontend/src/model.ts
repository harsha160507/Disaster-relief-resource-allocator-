export type Role = 'SYSTEM_ADMIN' | 'DISASTER_COORDINATOR' | 'INVENTORY_MANAGER' | 'FIELD_AGENT' | 'PARTNER_MANAGER' | 'VIEWER';
export type Row = Record<string, any>;
export interface Profile { id: string; displayName: string; email?: string; organizationId: string | null; roles: Role[]; }
export interface Page<T = Row> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }
export type Screen = 'dashboard' | 'inventory' | 'requests' | 'allocations' | 'shipments' | 'disasters' | 'locations' | 'resource-types' | 'organizations' | 'users' | 'audit-events';
export const roleLabels: Record<Role, string> = { SYSTEM_ADMIN:'System administrator', DISASTER_COORDINATOR:'Disaster coordinator', INVENTORY_MANAGER:'Inventory manager', FIELD_AGENT:'Field agent', PARTNER_MANAGER:'Partner manager', VIEWER:'Viewer' };
export const has = (profile: Profile, ...roles: Role[]) => profile.roles.includes('SYSTEM_ADMIN') || roles.some(role => profile.roles.includes(role));
export const number = (value: unknown) => new Intl.NumberFormat('en', { maximumFractionDigits: 3 }).format(Number(value ?? 0));
export const label = (value: string) => value.toLowerCase().replaceAll('_',' ').replace(/^./, c => c.toUpperCase());
export const statusLabel = (value: string) => ({ FULFILLED:'Fully allocated', PARTIALLY_FULFILLED:'Partially allocated', PARTIALLY_DELIVERED:'Partially delivered', DELIVERED:'Delivered', CONFIRMED:'Ready to dispatch' }[value] ?? label(value));
export const shortDate = (value?: string) => value ? new Date(value).toLocaleDateString('en', {day:'numeric',month:'short'}) : '—';
