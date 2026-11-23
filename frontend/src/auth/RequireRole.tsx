import type { ReactNode } from 'react';
import { Button, Result } from 'antd';
import { Link, Navigate, useLocation } from 'react-router-dom';
import type { Role } from '../api/auth';
import { useAuth } from './AuthContext';
import { FullPageSpinner } from '../components/common';

interface Props {
  /** Roles allowed to see the page. Empty means "any signed-in user". */
  roles?: readonly Role[];
  children: ReactNode;
}

/**
 * UI guard only: it hides pages a user cannot use. The server still checks every request
 * with @PreAuthorize, so changing this code in the browser gives no extra data.
 */
export function RequireRole({ roles = [], children }: Props) {
  const { user, ready } = useAuth();
  const location = useLocation();

  if (!ready) return <FullPageSpinner />;

  if (!user) {
    // Remember where the user wanted to go, so login can send them back there.
    const from = location.pathname + location.search;
    return <Navigate to="/login" replace state={{ from }} />;
  }

  if (roles.length > 0 && !roles.includes(user.role)) {
    return (
      <Result
        status="403"
        title="No access"
        subTitle="Your role cannot open this page."
        extra={
          <Link to="/">
            <Button type="primary">Back to dashboard</Button>
          </Link>
        }
      />
    );
  }

  return <>{children}</>;
}

/**
 * Where to go after login. Only in-app paths are accepted ("/students"), never "//evil.com"
 * or "/\evil.com", which browsers may treat as another site (open redirect).
 */
export function safeReturnPath(from: unknown): string {
  if (typeof from !== 'string' || !from.startsWith('/')) return '/';
  if (from.startsWith('//') || from.includes('\\') || from.startsWith('/login')) return '/';
  return from;
}
