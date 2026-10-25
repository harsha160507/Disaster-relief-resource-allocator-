import { useEffect, useState } from 'react';
import {
  ArrowRight,
  ArrowUpRight,
  Bell,
  CalendarDays,
  ChevronRight,
  Command,
  Globe2,
  Layers3,
  Moon,
  Plus,
  Search,
  ShieldCheck,
  Sun,
  X,
  CheckCircle2,
  RefreshCw,
  SlidersHorizontal,
} from 'lucide-react';
import { api, setDemo } from './lib/client';
import { configured, manager, signOut } from './lib/auth';
import { demoProfile } from './lib/demo';
import { has, statusLabel, type Profile, type Role, type Row, type Screen } from './model';
import { useCollection, useTheme } from './hooks';
import { Sidebar } from './components/Sidebar';
import { DataTable } from './components/DataTable';
import { Notice } from './components/ui';
import SquishSwitch from './components/supplied/SquishSwitch';
import { Dashboard } from './screens/Dashboard';
import { columns, createAction, descriptions, statusOptions, titles } from './screens/collections';
import { FormDialog, type FormAction } from './screens/Forms';
import { Details } from './screens/Details';
function Collection({
  screen,
  profile,
  refresh,
  onForm,
  onDetail,
}: {
  screen: Screen;
  profile: Profile;
  refresh: number;
  onForm: (kind: string, row?: Row) => void;
  onDetail: (screen: Screen, row: Row) => void;
}) {
  const table = useCollection(screen, refresh);
  const create = createAction(screen, profile);
  return (
    <>
      <section className="page-intro">
        <div>
          <span className="eyebrow">
            {['users', 'organizations', 'audit-events'].includes(screen)
              ? 'WORKSPACE MANAGEMENT'
              : 'RELIEF OPERATIONS'}
          </span>
          <h1>{titles[screen]}</h1>
          <p>{descriptions[screen]}</p>
        </div>
        {create && (
          <button className="ui-btn ui-btn-primary" onClick={() => onForm(create.kind)}>
            <Plus size={17} />
            {create.label}
          </button>
        )}
      </section>
      <section className="panel collection-panel">
        <header className="panel-header">
          <div>
            <h2>
              {titles[screen]}
              <span className="count-chip">{table.data.totalElements}</span>
            </h2>
            <p>Search, review and take the next step.</p>
          </div>
          {statusOptions[screen] && (
            <label className="filter-select">
              <SlidersHorizontal size={15} />
              <select
                aria-label="Filter by status"
                value={table.status}
                onChange={(e) => table.setStatus(e.target.value)}
              >
                <option value="">All statuses</option>
                {statusOptions[screen]!.map((value) => (
                  <option key={value} value={value}>
                    {statusLabel(value)}
                  </option>
                ))}
              </select>
            </label>
          )}
        </header>
        {table.error ? (
          <Notice error>{table.error}</Notice>
        ) : (
          <DataTable
            columns={columns(screen)}
            data={table.data}
            loading={table.loading}
            q={table.q}
            onQuery={table.setQ}
            onPage={table.setPage}
            sort={table.sort}
            onSort={screen === 'audit-events' ? undefined : table.setSort}
            search={screen !== 'audit-events'}
            onRow={(row) => onDetail(screen, row)}
            actions={(row) => (
              <button
                className="icon-button"
                aria-label={
                  'View ' +
                  (row.reference || row.name || row.resourceName || row.displayName || 'record')
                }
                onClick={() => onDetail(screen, row)}
              >
                <ArrowUpRight size={16} />
              </button>
            )}
          />
        )}
      </section>
      <footer className="workspace-footer">
        <span>Organization-scoped records · actions follow your role</span>
        <span>Disaster Relief Resource Allocator</span>
      </footer>
    </>
  );
}
export default function App() {
  const { theme, toggle } = useTheme();
  const [profile, setProfile] = useState<Profile | null>(null),
    [organization, setOrganization] = useState('Relief Operations Network'),
    [demo, setDemonstration] = useState(false),
    [initializing, setInitializing] = useState(true),
    [loginError, setLoginError] = useState(''),
    [screen, setScreen] = useState<Screen>('dashboard'),
    [refresh, setRefresh] = useState(0),
    [form, setForm] = useState<FormAction | null>(null),
    [detail, setDetail] = useState<{ screen: Screen; row: Row } | null>(null),
    [toast, setToast] = useState(''),
    [globalSearch, setGlobalSearch] = useState('');
  useEffect(() => {
    let cancelled = false;
    async function initialize() {
      try {
        if (window.location.pathname === '/auth/callback' && manager) {
          await manager.signinRedirectCallback();
          history.replaceState({}, '', '/');
        }
        const user = await manager?.getUser();
        if (user && !user.expired) {
          const me = await api<Profile>('/me');
          if (!cancelled) {
            setProfile(me);
            if (me.organizationId) {
              const org = await api<Row>('/me/organization');
              setOrganization(org.name);
            } else setOrganization('All organizations');
          }
        }
      } catch (e) {
        if (!cancelled) setLoginError((e as Error).message);
      } finally {
        if (!cancelled) setInitializing(false);
      }
    }
    initialize();
    const expired = () => {
      setProfile(null);
      setLoginError('Your session has expired. Sign in to continue.');
    };
    window.addEventListener('session-expired', expired);
    return () => {
      cancelled = true;
      window.removeEventListener('session-expired', expired);
    };
  }, []);
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(''), 5000);
    return () => clearTimeout(timer);
  }, [toast]);
  function explore() {
    setDemo(true);
    setDemonstration(true);
    setProfile({ ...demoProfile });
    setOrganization('Relief Operations Network');
    setScreen('dashboard');
  }
  function navigate(next: Screen) {
    setScreen(next);
    setDetail(null);
    setForm(null);
  }
  function changed(message: string) {
    setToast(message);
    setRefresh((r) => r + 1);
  }
  function completed(message: string, row?: Row) {
    const kind = form?.kind;
    setForm(null);
    changed(message);
    if (kind === 'requests' && row) setDetail({ screen: 'requests', row });
  }
  function exit() {
    if (demo) {
      setProfile(null);
      setDemo(false);
      setDemonstration(false);
    } else void signOut();
  }
  if (initializing)
    return (
      <div className="initializing">
        <span className="brand-text">Disaster Relief Resource Allocator</span>
        <p>Opening your workspace…</p>
      </div>
    );
  if (!profile)
    return (
      <div className="login-page">
        <header>
          <span className="brand-text">
            Disaster Relief
            <br />
            <small>Resource Allocator</small>
          </span>
          <button className="icon-button" aria-label="Toggle theme" onClick={toggle}>
            {theme === 'dark' ? <Sun size={20} /> : <Moon size={20} />}
          </button>
        </header>
        <main>
          <div className="login-story">
            <span className="eyebrow">COORDINATED RESOURCES. MEANINGFUL RELIEF.</span>
            <h1>
              Get the right resources
              <br />
              to the right people.
            </h1>
            <p>
              One workspace for inventory, requests and deliveries.
              <br />
              Built for the people making relief happen.
            </p>
            <div className="login-steps">
              <span>
                <Layers3 size={20} />
                Know your resources
              </span>
              <span>
                <Globe2 size={20} />
                Coordinate your response
              </span>
              <span>
                <ShieldCheck size={20} />
                Follow every delivery
              </span>
            </div>
            <div className="login-art" aria-hidden="true">
              <div className="orbit orbit-1" />
              <div className="orbit orbit-2" />
              <div className="relief-tile">
                <Layers3 size={34} />
              </div>
              <span className="art-label art-label-a">Resources, ready.</span>
              <span className="art-label art-label-b">
                <CheckCircle2 size={16} />
                Relief, delivered.
              </span>
            </div>
          </div>
          <section className="login-card">
            <span className="eyebrow">YOUR OPERATIONS WORKSPACE</span>
            <h2>Welcome back.</h2>
            <p>Sign in with your organization to continue.</p>
            {loginError && <Notice error>{loginError}</Notice>}
            <button
              className="ui-btn ui-btn-primary login-signin"
              disabled={!configured}
              onClick={() => manager?.signinRedirect().catch((e) => setLoginError(e.message))}
            >
              Sign in to your organization
              <ArrowRight size={17} />
            </button>
            {!configured && (
              <small className="setup-note">
                Organization sign-in becomes available after your administrator connects an identity
                provider.
              </small>
            )}
            <div className="login-divider">
              <span>or take a look around</span>
            </div>
            <button className="ui-btn demo-button" onClick={explore}>
              Explore demo workspace
              <ArrowUpRight size={17} />
            </button>
            <small>The demo uses sample data. It does not access live operations.</small>
            <div className="login-secure">
              <ShieldCheck size={15} />
              Organization access · Protected operations
            </div>
          </section>
        </main>
        <footer>
          Disaster Relief Resource Allocator<span>Every resource has a purpose.</span>
        </footer>
      </div>
    );
  return (
    <div className="app-shell">
      <Sidebar
        screen={screen}
        onNavigate={navigate}
        profile={profile}
        organization={organization}
        demo={demo}
        onExit={exit}
        onRole={(role: Role) => {
          setProfile({ ...profile, roles: [role] });
          navigate('dashboard');
          setRefresh((r) => r + 1);
        }}
      />
      <div className="app-main">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace
            <ChevronRight size={13} />
            <strong>{titles[screen]}</strong>
          </div>
          <div className="topbar-tools">
            {demo && (
              <span className="demo-label">
                <span />
                Demo workspace
              </span>
            )}
            <span className="theme-control">
              <Sun size={15} />
              <SquishSwitch
                checked={theme === 'dark'}
                onChange={toggle}
                width={40}
                height={22}
                radius={11}
                trackColor="var(--border)"
                trackOnColor="#2563eb"
                thumbColor="var(--surface)"
                thumbOnColor="#ffffff"
                ariaLabel="Dark theme"
                stretch={0.08}
              />
              <Moon size={14} />
            </span>
            <span className="topbar-divider" />
            <button
              className="icon-button"
              aria-label="Refresh workspace"
              onClick={() => setRefresh((r) => r + 1)}
            >
              <RefreshCw size={17} />
            </button>
            <span className="avatar small-avatar">
              {profile.displayName
                .split(' ')
                .map((n) => n[0])
                .slice(0, 2)
                .join('')}
            </span>
          </div>
        </header>
        <main className="workspace-content">
          {screen === 'dashboard' ? (
            <Dashboard
              profile={profile}
              refresh={refresh}
              onForm={(kind, row) => setForm({ kind, row })}
              onDetail={(screen, row) => setDetail({ screen, row })}
              onNavigate={navigate}
            />
          ) : (
            <Collection
              key={screen}
              screen={screen}
              profile={profile}
              refresh={refresh}
              onForm={(kind, row) => setForm({ kind, row })}
              onDetail={(screen, row) => setDetail({ screen, row })}
            />
          )}
        </main>
      </div>
      {detail && (
        <Details
          key={detail.row.id}
          screen={detail.screen}
          initial={detail.row}
          profile={profile}
          refresh={refresh}
          onClose={() => setDetail(null)}
          onForm={(kind, row) => setForm({ kind, row })}
          onChanged={changed}
          theme={theme}
        />
      )}
      {form && (
        <FormDialog
          key={form.kind + (form.row?.id || '')}
          action={form}
          profile={profile}
          onClose={() => setForm(null)}
          onDone={completed}
        />
      )}
      {toast && (
        <div className="toast" role="status">
          <span>
            <CheckCircle2 size={19} />
          </span>
          <div>
            <strong>Changes saved</strong>
            <p>{toast}</p>
          </div>
          <button
            className="icon-button"
            aria-label="Dismiss notification"
            onClick={() => setToast('')}
          >
            <X size={16} />
          </button>
        </div>
      )}
    </div>
  );
}
