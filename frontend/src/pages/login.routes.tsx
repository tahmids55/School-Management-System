import { lazy } from 'react';

// React.lazy: the sign-in page's code is downloaded only when somebody opens it.
const LoginPage = lazy(() => import('./LoginPage'));

export const path = '/login';
export const element = <LoginPage />;
// Shown outside the layout and without a login check (see routes/registry.ts).
export const isPublic = true;
