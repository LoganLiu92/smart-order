const API_BASE =
  (window as typeof window & { API_BASE?: string }).API_BASE ||
  import.meta.env.VITE_API_BASE ||
  "";

export type AuthMode = "store" | "platform";

type TokenKeys = { access: string; refresh: string };

type StoreIdentity = {
  role: string | null;
  storeId: string | null;
};

const TOKEN_KEYS: Record<AuthMode, TokenKeys> = {
  store: { access: "smartorder-access", refresh: "smartorder-refresh" },
  platform: { access: "platform-access", refresh: "platform-refresh" },
};

const STORE_KEYS = { role: "smartorder-role", storeId: "smartorder-store" } as const;

export function getAccessToken(mode: AuthMode) {
  return localStorage.getItem(TOKEN_KEYS[mode].access);
}

export function getRefreshToken(mode: AuthMode) {
  return localStorage.getItem(TOKEN_KEYS[mode].refresh);
}

export function setTokens(mode: AuthMode, accessToken?: string, refreshToken?: string) {
  const keys = TOKEN_KEYS[mode];
  if (accessToken) localStorage.setItem(keys.access, accessToken);
  if (refreshToken) localStorage.setItem(keys.refresh, refreshToken);
}

export function clearTokens(mode: AuthMode) {
  const keys = TOKEN_KEYS[mode];
  localStorage.removeItem(keys.access);
  localStorage.removeItem(keys.refresh);
  if (mode === "store") {
    localStorage.removeItem(STORE_KEYS.role);
    localStorage.removeItem(STORE_KEYS.storeId);
  }
}

export function setStoreIdentity(role?: string, storeId?: string) {
  if (role) localStorage.setItem(STORE_KEYS.role, role);
  if (storeId) localStorage.setItem(STORE_KEYS.storeId, storeId);
}

export function getStoreIdentity(): StoreIdentity {
  return {
    role: localStorage.getItem(STORE_KEYS.role),
    storeId: localStorage.getItem(STORE_KEYS.storeId),
  };
}

export async function refreshAccess(mode: AuthMode) {
  const refreshToken = getRefreshToken(mode);
  if (!refreshToken) return false;
  const res = await fetch(`${API_BASE}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) return false;
  const data = await res.json();
  setTokens(mode, data.accessToken, data.refreshToken);
  return true;
}

async function fetchWithAuth(path: string, options: RequestInit = {}) {
  const mode: AuthMode = path.startsWith("/api/platform") ? "platform" : "store";
  const headers = new Headers(options.headers || {});
  const accessToken = getAccessToken(mode);
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);

  let response = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (response.status === 401) {
    const refreshed = await refreshAccess(mode);
    if (refreshed) {
      const retryHeaders = new Headers(options.headers || {});
      const newAccess = getAccessToken(mode);
      if (newAccess) retryHeaders.set("Authorization", `Bearer ${newAccess}`);
      response = await fetch(`${API_BASE}${path}`, { ...options, headers: retryHeaders });
    }
  }

  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || "Request failed");
  }

  const contentType = response.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return response.json();
  }
  return response.text();
}

export async function apiGet(path: string) {
  return fetchWithAuth(path, { method: "GET" });
}

export async function apiPost(path: string, body?: unknown) {
  return fetchWithAuth(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body || {}),
  });
}

export async function apiPatch(path: string, body?: unknown) {
  return fetchWithAuth(path, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body || {}),
  });
}

export async function apiDelete(path: string) {
  return fetchWithAuth(path, { method: "DELETE" });
}

export async function uploadFile(path: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return fetchWithAuth(path, { method: "POST", body: formData });
}

export { API_BASE };
