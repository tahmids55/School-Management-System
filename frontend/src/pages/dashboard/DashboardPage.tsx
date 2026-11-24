import { Card, Empty } from 'antd';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader } from '../../components/common';
import { formatDate } from '../../utils/format';
import { schoolToday } from '../../utils/schoolDate';

/** Placeholder so the shell can be opened. F25 / F31 replace the content with real dashboards. */
export default function DashboardPage() {
  const { user } = useAuth();
  return (
    <>
      <PageHeader title="Dashboard" subtitle={`Welcome, ${user?.fullName ?? ''} · ${formatDate(schoolToday())}`} />
      <Card>
        <Empty description="Your school overview will appear here." />
      </Card>
    </>
  );
}
