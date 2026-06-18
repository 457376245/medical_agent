"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import {
  getToken,
  setToken,
  clearAll,
  setStoredUser,
  setCurrentPatientId,
  type AuthUser,
} from "../../lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL || "http://127.0.0.1:8080/api";

interface AuthContextType {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, displayName: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
      const token = getToken();
      if (!token) {
        clearAll();
        if (!cancelled) {
          setUser(null);
          setIsLoading(false);
        }
        return;
      }

      try {
        const res = await fetch(`${API_BASE}/auth/me`, {
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
        const body = await res.json().catch(() => null);

        if (!res.ok || !body || body.code !== "OK" || !body.data) {
          clearAll();
          if (!cancelled) {
            setUser(null);
            setIsLoading(false);
          }
          return;
        }

        const { userId, displayName, defaultPatientId } = body.data as AuthUser;
        const authUser: AuthUser = { userId, displayName, defaultPatientId };
        setStoredUser(authUser);
        if (!cancelled) {
          setUser(authUser);
          if (defaultPatientId) {
            setCurrentPatientId(defaultPatientId);
          }
          setIsLoading(false);
        }
      } catch {
        clearAll();
        if (!cancelled) {
          setUser(null);
          setIsLoading(false);
        }
      }
    };

    void restoreSession();

    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const res = await fetch(`${API_BASE}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password }),
    });
    const body = await res.json();
    if (!res.ok || body.code !== "OK") {
      throw new Error(body.message || "登录失败");
    }
    const { token, userId, displayName, defaultPatientId } = body.data;
    setToken(token);
    const authUser: AuthUser = { userId, displayName, defaultPatientId };
    setStoredUser(authUser);
    setUser(authUser);
    if (defaultPatientId) {
      setCurrentPatientId(defaultPatientId);
    }
  }, []);

  const register = useCallback(async (email: string, password: string, displayName: string) => {
    const res = await fetch(`${API_BASE}/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, displayName }),
    });
    const body = await res.json();
    if (!res.ok || body.code !== "OK") {
      throw new Error(body.message || "注册失败");
    }
  }, []);

  const logout = useCallback(() => {
    clearAll();
    setUser(null);
    window.location.href = "/login";
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
