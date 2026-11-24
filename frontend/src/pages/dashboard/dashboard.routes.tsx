import { lazy } from 'react';
import { DashboardOutlined } from '@ant-design/icons';
import type { MenuEntry } from '../../routes/registry';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { Role } from '../../api/auth';

// React.lazy: the page's code is downloaded only when somebody opens it.
const DashboardPage = lazy(() => import('./DashboardPage'));

export const path = '/';
export const element = <DashboardPage />;
export const roles: Role[] = ['ADMIN', 'TEACHER', 'STUDENT'];
export const menu: MenuEntry = { label: 'Dashboard', icon: <DashboardOutlined />, order: MENU_ORDER.Dashboard };
