import { Alert, Button, Card, Col, Empty, Row, Spin, Table } from 'antd';
import type { DayRecord } from '../../api/attendance';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader, StatCard, StatusTag } from '../../components/common';
import { thisSchoolYearRange, useStudentAttendanceThisYear } from '../../hooks/useStudentAttendance';
import { formatDate, formatPercent } from '../../utils/format';

/** /my/attendance (STUDENT): own attendance this year, four stat cards, a warning when low, the day list. */
export default function MyAttendancePage() {
  const { user } = useAuth();
  const { year } = thisSchoolYearRange();
  const query = useStudentAttendanceThisYear(user?.studentId);
  const summary = query.data;

  let body;
  if (!user?.studentId) {
    body = <Alert type="warning" showIcon message="No student profile is linked to this account" />;
  } else if (query.isLoading) {
    body = (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  } else if (query.isError) {
    body = (
      <Alert
        type="error"
        showIcon
        message={errorMessage(query.error)}
        action={
          <Button size="small" onClick={() => query.refetch()}>
            Retry
          </Button>
        }
      />
    );
  } else if (summary) {
    body = (
      <>
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={12} md={6}>
            <StatCard label="Attendance" value={formatPercent(summary.percentage)} tint={summary.low ? 'rose' : 'mint'} />
          </Col>
          <Col xs={12} md={6}>
            <StatCard label="Present" value={summary.present} tint="sky" />
          </Col>
          <Col xs={12} md={6}>
            <StatCard label="Late" value={summary.late} tint="yellow" />
          </Col>
          <Col xs={12} md={6}>
            <StatCard label="Absent" value={summary.absent} tint="peach" />
          </Col>
        </Row>

        {summary.low && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message="Your attendance is below the required level."
          />
        )}

        <Card styles={{ body: { padding: 0 } }}>
          <Table<DayRecord>
            rowKey="date"
            dataSource={summary.records}
            pagination={{ pageSize: 20, hideOnSinglePage: true }}
            scroll={{ x: 'max-content' }}
            columns={[
              { title: 'Date', dataIndex: 'date', render: (value: string) => formatDate(value) },
              { title: 'Status', dataIndex: 'status', render: (value: string) => <StatusTag value={value} /> },
              { title: 'Remarks', dataIndex: 'remarks', render: (value?: string | null) => value || '-' },
            ]}
            locale={{ emptyText: <Empty description="No attendance recorded yet" /> }}
          />
        </Card>
      </>
    );
  }

  return (
    <div>
      <PageHeader title="My attendance" subtitle={`This year (${year})`} />
      {body}
    </div>
  );
}
