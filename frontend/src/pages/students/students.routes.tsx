import { lazy } from 'react';
import { TeamOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const StudentsPage = lazy(() => import('./StudentsPage'));

export const path = '/students';
export const element = <StudentsPage />;
// ADMIN only in F13; F14 adds TEACHER once teachers can only see their own classes.
export const roles: Role[] = ['ADMIN'];
export const menu: MenuEntry = { label: 'Students', icon: <TeamOutlined />, order: MENU_ORDER.Students };
