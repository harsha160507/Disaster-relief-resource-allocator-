import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
const authority = import.meta.env.VITE_OIDC_AUTHORITY;
const clientId = import.meta.env.VITE_OIDC_CLIENT_ID;
export const configured = Boolean(authority && clientId);
export const manager = configured ? new UserManager({
  authority, client_id: clientId, redirect_uri: window.location.origin + '/auth/callback',
  post_logout_redirect_uri: window.location.origin, response_type: 'code',
  scope: import.meta.env.VITE_OIDC_SCOPE || 'openid profile email',
  extraQueryParams: import.meta.env.VITE_OIDC_AUDIENCE ? { audience: import.meta.env.VITE_OIDC_AUDIENCE } : {},
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: false, loadUserInfo: false
}) : null;
export async function token() {
  const user = await manager?.getUser();
  if (!user || user.expired) throw new Error('Your session has expired. Please sign in again.');
  return user.access_token;
}
export async function signOut() {
  if (!manager) return;
  try { await manager.signoutRedirect(); } catch { await manager.removeUser(); window.location.assign('/'); }
}
