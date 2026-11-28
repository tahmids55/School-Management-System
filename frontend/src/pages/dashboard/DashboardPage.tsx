import { useAuth } from '../../auth/AuthContext';
import AdminDashboardView from './AdminDashboardView';
import StudentDashboardView from './StudentDashboardView';
import TeacherDashboardView from './TeacherDashboardView';

/** One page, one view per role. Each view makes ONE request; the server's facade assembles it. */
export default function DashboardPage() {
  const { user } = useAuth();
  if (user?.role === 'ADMIN') return <AdminDashboardView />;
  if (user?.role === 'TEACHER') return <TeacherDashboardView />;
  return <StudentDashboardView />;
}
