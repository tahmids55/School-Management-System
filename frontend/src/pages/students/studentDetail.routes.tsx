import { lazy } from 'react';
import type { Role } from '../../api/auth';

const StudentDetailPage = lazy(() => import('./StudentDetailPage'));

// No menu entry: the "Students" menu item stays highlighted on /students/42.
export const path = '/students/:id';
export const element = <StudentDetailPage />;
export const roles: Role[] = ['ADMIN'];
