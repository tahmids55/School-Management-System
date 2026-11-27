import { lazy } from 'react';
import { TrophyOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const ResultsPage = lazy(() => import('./ResultsPage'));

export const path = '/results';
export const element = <ResultsPage />;
export const roles: Role[] = ['ADMIN', 'TEACHER'];
export const menu: MenuEntry = {
  label: 'Results',
  icon: <TrophyOutlined />,
  order: MENU_ORDER.Results,
};
