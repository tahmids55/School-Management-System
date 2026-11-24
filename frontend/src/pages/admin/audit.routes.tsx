import { lazy } from 'react';
import { AuditOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const AuditPage = lazy(() => import('./AuditPage'));

export const path = '/audit';
export const element = <AuditPage />;
export const roles: Role[] = ['ADMIN'];
export const menu: MenuEntry = {
  label: 'Audit Log',
  icon: <AuditOutlined />,
  order: MENU_ORDER['Audit Log'],
};
