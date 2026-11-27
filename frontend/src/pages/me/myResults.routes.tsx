import { lazy } from 'react';
import { TrophyOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const MyResultsPage = lazy(() => import('./MyResultsPage'));

export const path = '/my/results';
export const element = <MyResultsPage />;
export const roles: Role[] = ['STUDENT'];
export const menu: MenuEntry = {
  label: 'My Results',
  icon: <TrophyOutlined />,
  order: MENU_ORDER['My Results'],
};
