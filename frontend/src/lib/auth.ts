const TOKEN_KEY = "auth_token";
const USER_KEY = "auth_user";
const PATIENT_KEY = "current_patient_id";

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export function isAuthenticated(): boolean {
  return !!getToken();
}

export interface AuthUser {
  userId: string;
  displayName: string;
  defaultPatientId: string | null;
}

export function getStoredUser(): AuthUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

export function setStoredUser(user: AuthUser): void {
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearStoredUser(): void {
  localStorage.removeItem(USER_KEY);
}

export function getCurrentPatientId(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(PATIENT_KEY);
}

export function setCurrentPatientId(patientId: string): void {
  localStorage.setItem(PATIENT_KEY, patientId);
}

export function clearCurrentPatientId(): void {
  localStorage.removeItem(PATIENT_KEY);
}

export function clearAll(): void {
  clearToken();
  clearStoredUser();
  clearCurrentPatientId();
}
