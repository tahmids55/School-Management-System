import type { ReactNode } from 'react';
import type { Role } from '../api/auth';

export interface MenuEntry {
  label: string;
  icon?: ReactNode;
  /** Position in the sidebar: Dashboard 10, Students 20, ... Audit Log 140. */
  order: number;
}

/**
 * What every src/pages/<feature>/<name>.routes.tsx file exports.
 * Example: export const path = '/students'; export const element = <StudentsPage />; (a React.lazy component)
 */
export interface RouteModule {
  path: string;
  element: ReactNode;
  /** Roles allowed to open the page. Empty or missing = any signed-in user. */
  roles?: readonly Role[];
  /** Shown in the sidebar when present (and the user has the role). */
  menu?: MenuEntry;
  /** Public pages (like /login) are shown without the layout and without a login check. */
  isPublic?: boolean;
}

export interface AppRoute extends RouteModule {
  /** The file it came from, for error messages. */
  source: string;
}

// Vite finds the files at build time. Adding a new *.routes.tsx file is all a feature needs to do.
const modules = import.meta.glob<RouteModule>('../pages/**/*.routes.tsx', { eager: true });

export function buildRoutes(found: Record<string, RouteModule>): AppRoute[] {
  const routes: AppRoute[] = [];
  const seen = new Set<string>();
  for (const [source, module] of Object.entries(found)) {
    if (typeof module.path !== 'string' || !module.path.startsWith('/') || module.element === undefined) {
      throw new Error(`${source} must export a path starting with "/" and an element`);
    }
    if (seen.has(module.path)) throw new Error(`${source}: path ${module.path} is already registered`);
    seen.add(module.path);
    routes.push({ ...module, source });
  }
  return routes;
}

export const appRoutes: AppRoute[] = buildRoutes(modules);

/** Sidebar entries the given role may see, sorted by order. */
export function menuFor(role: Role | undefined, routes: AppRoute[] = appRoutes): AppRoute[] {
  return routes
    .filter((route) => route.menu && !route.isPublic)
    .filter((route) => !route.roles?.length || (role !== undefined && route.roles.includes(role)))
    .sort((a, b) => (a.menu?.order ?? 0) - (b.menu?.order ?? 0));
}

/**
 * The menu key to highlight: the longest menu path that matches the current URL.
 * "/students/42" highlights "/students"; "/" only matches "/" itself.
 */
export function selectedMenuKey(pathname: string, menuPaths: string[]): string | undefined {
  let best: string | undefined;
  for (const path of menuPaths) {
    const matches = path === '/' ? pathname === '/' : pathname === path || pathname.startsWith(`${path}/`);
    if (matches && (!best || path.length > best.length)) best = path;
  }
  return best;
}
