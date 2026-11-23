import { Suspense } from 'react';
import { Result } from 'antd';
import { Route, Routes } from 'react-router-dom';
import { RequireRole } from './auth/RequireRole';
import { FullPageSpinner } from './components/common';
import { AppLayout, NotFoundPage } from './layout/AppLayout';
import { appRoutes } from './routes/registry';

// Features never edit this file: they add src/pages/<feature>/<name>.routes.tsx instead.
const publicRoutes = appRoutes.filter((route) => route.isPublic);
const privateRoutes = appRoutes.filter((route) => !route.isPublic);
const hasLoginPage = publicRoutes.some((route) => route.path === '/login');

export default function App() {
  return (
    <Suspense fallback={<FullPageSpinner />}>
      <Routes>
        {publicRoutes.map((route) => (
          <Route key={route.path} path={route.path} element={route.element} />
        ))}

        {/* Until the sign-in screen (F02) adds its routes file, /login must still exist,
            otherwise the login redirect would land on the protected "not found" page in a loop. */}
        {!hasLoginPage && (
          <Route path="/login" element={<Result status="info" title="Sign-in is not available yet." />} />
        )}

        <Route
          element={
            <RequireRole>
              <AppLayout />
            </RequireRole>
          }
        >
          {privateRoutes.map((route) => (
            <Route
              key={route.path}
              path={route.path}
              element={<RequireRole roles={route.roles}>{route.element}</RequireRole>}
            />
          ))}
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  );
}
