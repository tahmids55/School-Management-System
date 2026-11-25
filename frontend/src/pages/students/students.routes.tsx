import { lazy } from 'react';
import { TeamOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const StudentsPage = lazy(() => import('./StudentsPage'));

export const path = '/students';
export const element = <StudentsPage />;
// Teachers see only students of their own classes (checked by the server); actions are admin-only.
export const roles: Role[] = ['ADMIN', 'TEACHER'];
export const menu: MenuEntry = { label: 'Students', icon: <TeamOutlined />, order: MENU_ORDER.Students };
