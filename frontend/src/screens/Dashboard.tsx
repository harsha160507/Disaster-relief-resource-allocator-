import { useEffect, useState } from 'react';
import {
  ArrowRight,
  ArrowUpRight,
  Boxes,
  MapPin,
  ClipboardList,
  Truck,
  Plus,
  Filter,
  ArrowDownToLine,
  CheckCircle2,
  Clock3,
  Package,
} from 'lucide-react';
import { api, isDemo } from '../lib/client';
import { useCollection } from '../hooks';
import { has, number, type Profile, type Row, type Screen } from '../model';
import { DataTable } from '../components/DataTable';
import { columns } from './collections';
import { Badge, Notice } from '../components/ui';
import CountUp from '../components/supplied/CountUp';
export function Dashboard({
  profile,
  refresh,
  onForm,
  onDetail,
  onNavigate,
}: {
  profile: Profile;
  refresh: number;
  onForm: (kind: string, row?: Row) => void;
  onDetail: (screen: Screen, row: Row) => void;
  onNavigate: (screen: Screen) => void;
}) {
  const inventoryManager = has(profile, 'INVENTORY_MANAGER');
  const table = useCollection(inventoryManager ? 'inventory' : 'requests', refresh);
  const requests = useCollection('requests', refresh);
  const [summary, setSummary] = useState<Record<string, number>>({}),
    [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    api<Record<string, number>>('/workspace/overview', { signal: controller.signal })
      .then(setSummary)
      .catch((e) => {
        if (e.name !== 'AbortError') setError(e.message);
      });
    return () => controller.abort();
  }, [refresh]);
  const cards = [
    {
      label: 'Resource lines',
      value: summary.inventoryLines,
      Icon: Boxes,
      note: 'Across your relief network',
      tone: 'blue',
    },
    {
      label: 'Relief locations',
      value: summary.locations,
      Icon: MapPin,
      note: 'Depots and distribution points',
      tone: 'violet',
    },
    {
      label: 'Open requests',
      value: summary.openRequests,
      Icon: ClipboardList,
      note: 'Awaiting allocation or delivery',
      tone: 'amber',
    },
    {
      label: 'Shipments in transit',
      value: summary.inTransit,
      Icon: Truck,
      note: 'Dispatched, awaiting delivery',
      tone: 'green',
    },
  ];
  return (
    <>
      <section className="page-intro">
        <div>
          <span className="eyebrow">RESOURCE OPERATIONS</span>
          <h1>Your resources. Their relief.</h1>
          <p>A clear picture of your resources. A faster path to relief.</p>
        </div>
        <div className="intro-actions">
          {has(profile, 'INVENTORY_MANAGER', 'DISASTER_COORDINATOR') && (
            <button className="ui-btn" onClick={() => onForm('shipment')}>
              <Truck size={16} />
              Create shipment
            </button>
          )}
          {inventoryManager && (
            <button className="ui-btn ui-btn-primary" onClick={() => onForm('receive')}>
              <Plus size={17} />
              Receive stock
            </button>
          )}
        </div>
      </section>
      {error && <Notice error>{error}</Notice>}
      <div className="stat-grid">
        {cards.map(({ label, value, Icon, note, tone }) => (
          <article className="stat-card" key={label}>
            <div className="stat-label">
              {label}
              <span className={'stat-icon ' + tone}>
                <Icon size={17} />
              </span>
            </div>
            <div className="stat-value">
              {value === undefined ? '—' : <CountUp to={value} duration={0.6} trigger="load" />}
            </div>
            <p>
              <span className={'tiny-dot ' + tone} />
              {note}
            </p>
          </article>
        ))}
      </div>
      <div className="dashboard-grid">
        <section className="panel inventory-panel">
          <header className="panel-header">
            <div>
              <h2>
                {inventoryManager ? 'Inventory at a glance' : 'Requests at a glance'}
                <span className="count-chip">{table.data.totalElements}</span>
              </h2>
              <p>
                {inventoryManager
                  ? 'Available, reserved and ready to make a difference.'
                  : 'See what your response teams need next.'}
              </p>
            </div>
            <button
              className="text-link"
              onClick={() => onNavigate(inventoryManager ? 'inventory' : 'requests')}
            >
              View all
              <ArrowUpRight size={15} />
            </button>
          </header>
          <div className="table-tabs">
            <button className={!table.status ? 'selected' : ''} onClick={() => table.setStatus('')}>
              All resources
            </button>
            {inventoryManager && (
              <>
                <button
                  className={table.status === 'AVAILABLE' ? 'selected' : ''}
                  onClick={() => table.setStatus('AVAILABLE')}
                >
                  Available
                </button>
                <button
                  className={table.status === 'RESERVED' ? 'selected' : ''}
                  onClick={() => table.setStatus('RESERVED')}
                >
                  Reserved
                </button>
              </>
            )}
            <span />
            <span className="auto-note">
              <span className="connection-dot" />
              Current snapshot
            </span>
          </div>
          {table.error ? (
            <Notice error>{table.error}</Notice>
          ) : (
            <DataTable
              columns={columns(inventoryManager ? 'inventory' : 'requests')}
              data={table.data}
              loading={table.loading}
              q={table.q}
              onQuery={table.setQ}
              onPage={table.setPage}
              sort={table.sort}
              onSort={table.setSort}
              onRow={(row) => onDetail(inventoryManager ? 'inventory' : 'requests', row)}
            />
          )}
        </section>
        <aside className="dashboard-aside">
          <section className="panel demand-panel">
            <header className="panel-header">
              <div>
                <h2>Response queue</h2>
                <p>Requests that need the next step.</p>
              </div>
              <span className="queue-icon">
                <ClipboardList size={17} />
              </span>
            </header>
            <div className="queue-list">
              {requests.data.content
                .filter((r) =>
                  ['APPROVED', 'SUBMITTED', 'PARTIALLY_FULFILLED', 'PARTIALLY_DELIVERED'].includes(
                    r.status,
                  ),
                )
                .slice(0, 4)
                .map((row) => (
                  <button
                    className="queue-item"
                    key={row.id}
                    onClick={() => onDetail('requests', row)}
                  >
                    <div className="queue-top">
                      <strong>{row.reference}</strong>
                      <span className={'priority ' + String(row.priority).toLowerCase()}>
                        <i />
                        {row.priority === 'CRITICAL' ? 'Critical' : 'High'}
                      </span>
                    </div>
                    <span>{row.destinationName || 'Relief destination'}</span>
                    <div className="queue-bottom">
                      <Badge status={row.status} />
                      <ArrowRight size={15} />
                    </div>
                  </button>
                ))}
              {!requests.loading && !requests.data.content.length && (
                <p className="muted small">No requests waiting. Your queue is clear.</p>
              )}
            </div>
            <button className="queue-footer" onClick={() => onNavigate('requests')}>
              Open request workspace
              <ArrowRight size={15} />
            </button>
          </section>
          <section className="dispatch-note">
            <span className="dispatch-note-icon">
              <Truck size={22} />
            </span>
            <h3>From shelf to shelter.</h3>
            <p>
              Bring reserved resources together into a shipment. Stock leaves inventory once, at
              dispatch.
            </p>
            <button onClick={() => onNavigate('shipments')}>
              Track deliveries
              <ArrowUpRight size={15} />
            </button>
          </section>
        </aside>
      </div>
      <footer className="workspace-footer">
        <span>
          <span className="connection-dot" />
          {isDemo()
            ? 'Sample workspace · changes stay in this demo'
            : 'Organization-scoped data · protected by your role'}
        </span>
        <span>Every resource has a purpose.</span>
      </footer>
    </>
  );
}
