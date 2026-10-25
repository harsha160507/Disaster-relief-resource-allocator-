import { beforeEach, describe, it, expect, vi } from 'vitest';
vi.mock('../lib/auth', () => ({ token: async () => 'test-access-token' }));
import { api, setDemo, ApiError } from '../lib/client';
describe('API transport boundaries', () => {
  beforeEach(() => {
    setDemo(false);
    vi.restoreAllMocks();
  });
  it('does not send demo requests to a live API', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch');
    setDemo(true);
    const profile = await api('/me');
    expect(profile.id).toBe('demo-user');
    expect(fetch).not.toHaveBeenCalled();
  });
  it('sends the bearer token without browser credentials', async () => {
    const fetch = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    await api('/workspace/overview');
    expect(fetch.mock.calls[0][1]).toMatchObject({
      credentials: 'omit',
      headers: { Authorization: 'Bearer test-access-token' },
    });
  });
  it('reports expired sessions and preserves server error messages', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ message: 'Sign in again' }), { status: 401 }),
    );
    const listener = vi.fn();
    window.addEventListener('session-expired', listener);
    await expect(api('/me')).rejects.toBeInstanceOf(ApiError);
    expect(listener).toHaveBeenCalledOnce();
    window.removeEventListener('session-expired', listener);
  });
});
