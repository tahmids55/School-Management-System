import { lazy } from 'react';
import { WalletOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const MyFeesPage = lazy(() => import('./MyFeesPage'));

export const path = '/my/fees';
export const element = <MyFeesPage />;
export const roles: Role[] = ['STUDENT'];
export const menu: MenuEntry = {
  label: 'My Fees',
  icon: <WalletOutlined />,
  order: MENU_ORDER['My Fees'],
};
