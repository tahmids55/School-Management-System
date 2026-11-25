import { lazy } from 'react';
import { IdcardOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const TeachersPage = lazy(() => import('./TeachersPage'));

export const path = '/teachers';
export const element = <TeachersPage />;
export const roles: Role[] = ['ADMIN', 'TEACHER'];
export const menu: MenuEntry = {
  label: 'Teachers',
  icon: <IdcardOutlined />,
  order: MENU_ORDER.Teachers,
};
