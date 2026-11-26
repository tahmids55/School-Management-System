import { Alert, Button, Empty, Space, Spin, Table, Typography } from 'antd';
import type { DayRecord } from '../../api/attendance';
import { errorMessage } from '../../api/client';
import { StatusTag } from '../../components/common';
import { useStudentAttendanceThisYear } from '../../hooks/useStudentAttendance';
import { formatDate, formatPercent } from '../../utils/format';

const { Text } = Typography;

/** Attendance of one student this year, newest day first (F20). */
export default function StudentAttendanceTab({ studentId }: { studentId: number }) {
  const query = useStudentAttendanceThisYear(studentId);

  if (query.isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
        <Spin />
      </div>
    );
  }
  if (query.isError) {
    return (
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
  }
  const summary = query.data;
  if (!summary) return null;

  return (
    <Space direction="vertical" size={12} style={{ width: '100%' }}>
      <Text type={summary.low ? 'danger' : 'secondary'}>
        {formatPercent(summary.percentage)} · Present {summary.present} · Late {summary.late} · Absent{' '}
        {summary.absent} · Excused {summary.excused}
      </Text>
      <Table<DayRecord>
        rowKey="date"
        size="small"
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
    </Space>
  );
}
