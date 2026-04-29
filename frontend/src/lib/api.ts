import { getToken, getCurrentPatientId, clearAll } from "./auth";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://35.208.147.180:8080/api";
const AGENT_API_BASE = process.env.NEXT_PUBLIC_AGENT_API_BASE_URL || "http://35.208.147.180:8090/api/v1";

function buildAuthHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra);
  const token = getToken();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const patientId = getCurrentPatientId();
  if (patientId) headers.set("X-Patient-Id", patientId);
  return headers;
}

function handle401(res: Response): Response {
  if (res.status === 401) {
    clearAll();
    if (typeof window !== "undefined") window.location.href = "/login";
    throw new Error("未登录或登录已过期");
  }
  return res;
}

/** Low-level fetch with auth headers. Returns raw Response for callers that need status code inspection. */
export async function authFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = buildAuthHeaders(options.headers);
  const url = path.startsWith("http") ? path : `${API_BASE}${path}`;
  const res = await fetch(url, { ...options, headers });
  return handle401(res);
}

/** Low-level fetch for the Agent (Python) API with auth headers. */
export async function agentFetch(
  path: string,
  options: RequestInit = {},
): Promise<Response> {
  const headers = buildAuthHeaders(options.headers);
  const url = path.startsWith("http") ? path : `${AGENT_API_BASE}${path}`;
  const res = await fetch(url, { ...options, headers });
  return handle401(res);
}

/** High-level fetch that auto-parses JSON and throws on error. */
export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<{ code: string; message: string; requestId: string; data: T }> {
  const headers = buildAuthHeaders(options.headers);

  if (!headers.has("Content-Type") && options.body && typeof options.body === "string") {
    headers.set("Content-Type", "application/json");
  }

  const url = path.startsWith("http") ? path : `${API_BASE}${path}`;
  const res = await fetch(url, { ...options, headers });

  if (res.status === 401) {
    clearAll();
    if (typeof window !== "undefined") {
      window.location.href = "/login";
    }
    throw new Error("未登录或登录已过期");
  }

  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `请求失败: ${res.status}`);
  }

  return res.json();
}

export { API_BASE, AGENT_API_BASE };
