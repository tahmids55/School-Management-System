import { lazy } from 'react';
import { WalletOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const FeesPage = lazy(() => import('./FeesPage'));

export const path = '/fees';
export const element = <FeesPage />;
export const roles: Role[] = ['ADMIN'];
export const menu: MenuEntry = {
  label: 'Fees',
  icon: <WalletOutlined />,
  order: MENU_ORDER.Fees,
};
