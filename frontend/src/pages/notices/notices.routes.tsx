import { lazy } from 'react';
import { NotificationOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const NoticesPage = lazy(() => import('./NoticesPage'));

export const path = '/notices';
export const element = <NoticesPage />;
export const roles: Role[] = ['ADMIN', 'TEACHER', 'STUDENT'];
export const menu: MenuEntry = {
  label: 'Notices',
  icon: <NotificationOutlined />,
  order: MENU_ORDER.Notices,
};
