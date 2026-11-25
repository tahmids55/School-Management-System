import { lazy } from 'react';
import { UserSwitchOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const UsersPage = lazy(() => import('./UsersPage'));

export const path = '/users';
export const element = <UsersPage />;
export const roles: Role[] = ['ADMIN'];
export const menu: MenuEntry = {
  label: 'User Accounts',
  icon: <UserSwitchOutlined />,
  order: MENU_ORDER['User Accounts'],
};
