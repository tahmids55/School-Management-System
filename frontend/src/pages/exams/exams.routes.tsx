import { lazy } from 'react';
import { FileTextOutlined } from '@ant-design/icons';
import type { Role } from '../../api/auth';
import { MENU_ORDER } from '../../routes/menuOrder';
import type { MenuEntry } from '../../routes/registry';

const ExamsPage = lazy(() => import('./ExamsPage'));

export const path = '/exams';
export const element = <ExamsPage />;
export const roles: Role[] = ['ADMIN', 'TEACHER'];
export const menu: MenuEntry = {
  label: 'Exams & Marks',
  icon: <FileTextOutlined />,
  order: MENU_ORDER['Exams & Marks'],
};
