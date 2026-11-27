import { lazy } from 'react';
import { CalendarOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const MyAttendancePage = lazy(() => import('./MyAttendancePage'));

export const path = '/my/attendance';
export const element = <MyAttendancePage />;
export const roles: Role[] = ['STUDENT'];
export const menu: MenuEntry = {
  label: 'My Attendance',
  icon: <CalendarOutlined />,
  order: MENU_ORDER['My Attendance'],
};
