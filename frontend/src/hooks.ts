import { useCallback, useEffect, useState } from 'react';
import { api } from './lib/client';
import type { Page, Row } from './model';
export const emptyPage: Page = { content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 };
export function useCollection(kind: string, refresh = 0, extra: Record<string, string> = {}) {
  const [data, setData] = useState<Page>(emptyPage),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(''),
    [q, setQ] = useState(''),
    [page, setPage] = useState(0),
    [status, setStatus] = useState(''),
    [sort, setSort] = useState('createdAt:desc');
  const extraKey = JSON.stringify(extra);
  const reload = useCallback(
    (signal: AbortSignal) => {
      const parameters = new URLSearchParams({
        page: String(page),
        size: '8',
        q,
        ...JSON.parse(extraKey),
      });
      if (status) parameters.set('status', status);
      const [field, direction] = sort.split(':');
      parameters.set('sort', field);
      parameters.set('direction', direction);
      const path = kind === 'audit-events' ? '/admin/audit-events' : '/workspace/' + kind;
      setLoading(true);
      setError('');
      api<Page>(path + '?' + parameters, { signal })
        .then((result) => {
          const content = (result.content || []).map((row: Row) =>
            row.request
              ? { ...row.request, ...row, request: undefined }
              : row.allocation
                ? { ...row.allocation, ...row, allocation: undefined }
                : row,
          );
          setData({ ...result, content });
        })
        .catch((e) => {
          if (e.name !== 'AbortError') setError(e.message);
        })
        .finally(() => {
          if (!signal.aborted) setLoading(false);
        });
    },
    [kind, page, q, status, sort, extraKey],
  );
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => reload(controller.signal), q ? 250 : 0);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [reload, refresh]);
  return {
    data,
    loading,
    error,
    q,
    setQ: (value: string) => {
      setQ(value);
      setPage(0);
    },
    page,
    setPage,
    status,
    setStatus: (value: string) => {
      setStatus(value);
      setPage(0);
    },
    sort,
    setSort: (key: string) => {
      setSort((current) => (current === key + ':asc' ? key + ':desc' : key + ':asc'));
      setPage(0);
    },
  };
}
export function useTheme() {
  const [theme, setTheme] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('relief-theme');
    return saved === 'light' || saved === 'dark'
      ? saved
      : matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'light';
  });
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('relief-theme', theme);
    document
      .querySelector('meta[name=theme-color]')
      ?.setAttribute('content', theme === 'dark' ? '#101725' : '#f5f7fa');
  }, [theme]);
  return { theme, toggle: () => setTheme((value) => (value === 'dark' ? 'light' : 'dark')) };
}
