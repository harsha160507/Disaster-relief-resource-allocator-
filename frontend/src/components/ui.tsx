import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { X, AlertCircle, Search, ChevronDown, Check, LoaderCircle } from 'lucide-react';
import { api } from '../lib/client';
import { statusLabel, type Page, type Row } from '../model';
export function Badge({ status }: { status: string }) {
  const tone = ['DELIVERED', 'ACTIVE', 'AVAILABLE', 'APPROVED'].includes(status)
    ? 'green'
    : ['REJECTED', 'FAILED', 'EMPTY', 'INACTIVE'].includes(status)
      ? 'red'
      : ['SUBMITTED', 'CRITICAL', 'PARTIALLY_FULFILLED', 'PARTIALLY_DELIVERED'].includes(status)
        ? 'amber'
        : ['CANCELLED', 'CLOSED', 'DRAFT'].includes(status)
          ? 'neutral'
          : 'blue';
  return (
    <span className={'badge ' + tone}>
      <i />
      {statusLabel(status)}
    </span>
  );
}
export function Notice({ children, error = false }: { children: ReactNode; error?: boolean }) {
  return (
    <div className={'notice ' + (error ? 'error' : '')} role={error ? 'alert' : 'status'}>
      <AlertCircle size={16} />
      <span>{children}</span>
    </div>
  );
}
export function Empty({
  title = 'Nothing here yet',
  text = 'Records will appear here once your team adds them.',
  retry,
}: {
  title?: string;
  text?: string;
  retry?: () => void;
}) {
  return (
    <div className="empty">
      <div className="empty-symbol">◇</div>
      <h3>{title}</h3>
      <p>{text}</p>
      {retry && (
        <button className="ui-btn" onClick={retry}>
          Try again
        </button>
      )}
    </div>
  );
}
export function Loading() {
  return (
    <div className="loading-state" role="status" aria-label="Loading records">
      {[1, 2, 3, 4, 5].map((i) => (
        <div className="skeleton" key={i} />
      ))}
    </div>
  );
}
export function Modal({
  title,
  description,
  children,
  onClose,
  busy = false,
  wide = false,
}: {
  title: string;
  description?: string;
  children: ReactNode;
  onClose: () => void;
  busy?: boolean;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const trigger = document.activeElement as HTMLElement;
    ref.current?.showModal();
    ref.current?.querySelector<HTMLButtonElement>('[data-close]')?.focus();
    return () => {
      ref.current?.close();
      trigger?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={'modal ' + (wide ? 'wide' : '')}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current && !busy) onClose();
      }}
    >
      <header className="modal-header">
        <div>
          <h2>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        <button
          className="icon-button"
          data-close
          aria-label="Close dialog"
          disabled={busy}
          onClick={onClose}
        >
          <X size={20} />
        </button>
      </header>
      {children}
    </dialog>
  );
}
export function Field({
  label,
  children,
  hint,
}: {
  label: string;
  children: ReactNode;
  hint?: string;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      <span className="field-control">{children}</span>
      {hint && <small>{hint}</small>}
    </label>
  );
}
export function Submit({
  busy,
  children = 'Save changes',
}: {
  busy: boolean;
  children?: ReactNode;
}) {
  return (
    <button className="ui-btn ui-btn-primary" type="submit" disabled={busy}>
      {busy ? (
        <>
          <LoaderCircle size={16} className="spin" /> Saving…
        </>
      ) : (
        children
      )}
    </button>
  );
}
export function EntitySelect({
  kind,
  value,
  onChange,
  label,
  required = true,
}: {
  kind: string;
  value: string;
  onChange: (value: string, row?: Row) => void;
  label: string;
  required?: boolean;
}) {
  const [open, setOpen] = useState(false),
    [q, setQ] = useState(''),
    [rows, setRows] = useState<Row[]>([]),
    [selected, setSelected] = useState(''),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null),
    id = useId();
  useEffect(() => {
    const outside = (e: PointerEvent) => {
      if (!root.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('pointerdown', outside);
    return () => document.removeEventListener('pointerdown', outside);
  }, []);
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    setBusy(true);
    setError('');
    const timer = setTimeout(() => {
      api<Page>('/workspace/' + kind + '?size=30&q=' + encodeURIComponent(q), {
        signal: controller.signal,
      })
        .then((data) => {
          setRows(data.content);
          setActive(0);
        })
        .catch((e) => {
          if (e.name !== 'AbortError') setError(e.message);
        })
        .finally(() => setBusy(false));
    }, 180);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [kind, q, open]);
  const optionText = (row: Row) =>
    kind === 'inventory'
      ? row.resourceName + ' · ' + row.locationName + ' · ' + row.availableQuantity + ' available'
      : row.name || row.displayName || row.reference || row.resourceName || row.id;
  const choose = (row: Row) => {
    setSelected(optionText(row));
    onChange(row.id, row);
    setOpen(false);
    setQ('');
  };
  return (
    <div className="field">
      <span>{label}</span>
      <div className="entity-select" ref={root}>
        <div className="select-input">
          <Search size={15} />
          <input
            aria-label={label}
            role="combobox"
            aria-expanded={open}
            aria-controls={id}
            aria-activedescendant={open && rows[active] ? id + '-' + active : undefined}
            placeholder={'Select ' + label.toLowerCase()}
            value={open ? q : selected || value}
            required={required && !value}
            onChange={(e) => {
              setQ(e.target.value);
              onChange('');
              setSelected('');
              setOpen(true);
            }}
            onFocus={() => setOpen(true)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') setOpen(false);
              if (e.key === 'ArrowDown') {
                e.preventDefault();
                setOpen(true);
                setActive((a) => Math.min(a + 1, rows.length - 1));
              }
              if (e.key === 'ArrowUp') {
                e.preventDefault();
                setActive((a) => Math.max(0, a - 1));
              }
              if (e.key === 'Enter' && open) {
                e.preventDefault();
                if (!busy && rows[active]) choose(rows[active]);
              }
            }}
          />
          <ChevronDown size={15} />
        </div>
        {open && (
          <div className="select-menu" id={id} role="listbox" aria-label={label}>
            {busy ? (
              <p>Loading…</p>
            ) : error ? (
              <p role="alert">{error}</p>
            ) : rows.length ? (
              rows.map((row, index) => (
                <button
                  type="button"
                  role="option"
                  id={id + '-' + index}
                  key={row.id}
                  aria-selected={value === row.id}
                  className={index === active ? 'highlighted' : ''}
                  onClick={() => choose(row)}
                >
                  {optionText(row)}
                  {value === row.id && <Check size={14} />}
                </button>
              ))
            ) : (
              <p>No matching records</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
