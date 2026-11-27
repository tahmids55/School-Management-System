import { Card, Empty } from 'antd';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/common';
import { formatDate } from '../../utils/format';
import { schoolToday } from '../../utils/schoolDate';
import AdminDashboardView from './AdminDashboardView';
import TeacherDashboardView from './TeacherDashboardView';

/** One page, one view per role. Each view makes ONE request; the server's facade assembles it. */
export default function DashboardPage() {
  const { user } = useAuth();
  if (user?.role === 'ADMIN') return <AdminDashboardView />;
  if (user?.role === 'TEACHER') return <TeacherDashboardView />;

  // Student dashboard: F31.
  return (
    <>
      <PageHeader title="Dashboard" subtitle={`Welcome, ${user?.fullName ?? ''} · ${formatDate(schoolToday())}`} />
      <Card>
        <Empty description="Your school overview will appear here." />
      </Card>
    </>
  );
}
