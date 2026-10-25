import { token } from './auth';
import { demoRequest } from './demo';
export class ApiError extends Error {
  constructor(message: string, public status: number, public fields: Record<string,string> = {}) { super(message); }
}
let demonstration = false;
export const setDemo = (enabled: boolean) => { demonstration = enabled; };
export const isDemo = () => demonstration;
export async function api<T = any>(path: string, init: RequestInit = {}): Promise<T> {
  if (demonstration) return demoRequest(path, init) as Promise<T>;
  const accessToken = await token();
  const response = await fetch((import.meta.env.VITE_API_BASE_URL || '') + '/api/v1' + path, {
    ...init, credentials: 'omit', headers: { 'Content-Type':'application/json', ...init.headers, Authorization:'Bearer ' + accessToken }
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    if (response.status === 401) window.dispatchEvent(new Event('session-expired'));
    throw new ApiError(error.message || 'The request could not be completed.', response.status, error.fieldErrors || error.fields || {});
  }
  if (response.status === 204) return undefined as T;
  return response.json();
}
export const send = <T = any>(path: string, body?: unknown, method = 'POST') => api<T>(path, { method, ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
