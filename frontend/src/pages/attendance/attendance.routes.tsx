import { lazy } from 'react';
import { CalendarOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const AttendancePage = lazy(() => import('./AttendancePage'));

export const path = '/attendance';
export const element = <AttendancePage />;
export const roles: Role[] = ['ADMIN', 'TEACHER'];
export const menu: MenuEntry = {
  label: 'Attendance',
  icon: <CalendarOutlined />,
  order: MENU_ORDER.Attendance,
};
