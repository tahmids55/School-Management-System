import { lazy } from 'react';
import { BookOutlined } from '@ant-design/icons';
import type { MenuEntry } from '../../routes/registry';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { Role } from '../../api/auth';

// React.lazy: the page's code is downloaded only when somebody opens it.
const AcademicsPage = lazy(() => import('./AcademicsPage'));

export const path = '/academics';
export const element = <AcademicsPage />;
export const roles: Role[] = ['ADMIN'];
export const menu: MenuEntry = { label: 'Academics', icon: <BookOutlined />, order: MENU_ORDER.Academics };
