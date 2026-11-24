import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import * as authApi from '../api/auth';
import type { CurrentUser, LoginRequest, Role } from '../api/auth';
import { setAccessToken, setSessionEndedHandler } from '../api/client';

export interface AuthState {
  /** The signed-in user, or null when nobody is signed in. */
  user: CurrentUser | null;
  /** False until the first refresh attempt on page load has finished. */
  ready: boolean;
  login: (request: LoginRequest) => Promise<CurrentUser>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [ready, setReady] = useState(false);

  // Forget everything about the previous user: token, cached server data, user object.
  const clearSession = useCallback(() => {
    setAccessToken(null);
    queryClient.clear();
    setUser(null);
  }, [queryClient]);

  // On page load the in-memory token is gone, so try the HttpOnly refresh cookie once.
  useEffect(() => {
    let cancelled = false;
    authApi
      .refresh()
      .then((session) => {
        if (!cancelled) setUser(session.user);
      })
      .catch(() => {
        if (!cancelled) setUser(null);
      })
      .finally(() => {
        if (!cancelled) setReady(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  // The API client calls this when a refresh fails in the middle of using the app.
  useEffect(() => {
    setSessionEndedHandler(clearSession);
    return () => setSessionEndedHandler(() => {});
  }, [clearSession]);

  const login = useCallback(
    async (request: LoginRequest) => {
      const session = await authApi.login(request);
      queryClient.clear();
      setAccessToken(session.accessToken);
      setUser(session.user);
      return session.user;
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch {
      // Even if the server is unreachable, the browser must forget the session.
    } finally {
      clearSession();
    }
  }, [clearSession]);

  const refreshUser = useCallback(async () => {
    setUser(await authApi.me());
  }, []);

  const hasRole = useCallback((...roles: Role[]) => !!user && roles.includes(user.role), [user]);

  const value = useMemo<AuthState>(
    () => ({ user, ready, login, logout, refreshUser, hasRole }),
    [user, ready, login, logout, refreshUser, hasRole],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>');
  return context;
}
