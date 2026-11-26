import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { Alert, Button, Card, DatePicker, Empty, Select, Space, Spin, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { attendanceKeys, getClassReport, type StudentAttendance } from '../../api/attendance';
import { errorMessage } from '../../api/client';
import { useMyClasses } from '../../hooks/useMyClasses';
import { colors } from '../../theme';
import { formatPercent, toIsoDate } from '../../utils/format';
import { schoolToday } from '../../utils/schoolDate';

const { Text } = Typography;
const { RangePicker } = DatePicker;

/** Tab "Report" of /attendance (F20): percentages of a class between two dates, low rows highlighted. */
export default function AttendanceReportTab() {
  const today = schoolToday();
  const [classId, setClassId] = useState<number | undefined>(undefined);
  // Default range: the first day of this month up to today (school time).
  const [range, setRange] = useState<[string, string]>([`${today.slice(0, 7)}-01`, today]);
  const [from, to] = range;

  const classesQuery = useMyClasses();

  const reportQuery = useQuery({
    queryKey: attendanceKeys.report(classId ?? 0, from, to),
    queryFn: () => getClassReport(classId!, from, to),
    enabled: classId !== undefined,
  });

  const report = reportQuery.data;
  const lowCount = report ? report.students.filter((s) => s.low).length : 0;

  const columns: ColumnsType<StudentAttendance> = [
    { title: 'Roll', dataIndex: 'rollNo', key: 'rollNo', width: 70, render: (v?: number | null) => v ?? '-' },
    {
      title: 'Student',
      dataIndex: 'studentName',
      key: 'studentName',
      render: (name: string) => <Text strong>{name}</Text>,
    },
    { title: 'Present', dataIndex: 'present', key: 'present', width: 90, align: 'right' },
    { title: 'Late', dataIndex: 'late', key: 'late', width: 80, align: 'right' },
    { title: 'Absent', dataIndex: 'absent', key: 'absent', width: 90, align: 'right' },
    { title: 'Excused', dataIndex: 'excused', key: 'excused', width: 90, align: 'right' },
    {
      title: 'Attendance',
      dataIndex: 'percentage',
      key: 'percentage',
      width: 120,
      align: 'right',
      render: (value: number, row) => (
        <Text strong type={row.low ? 'danger' : undefined}>
          {formatPercent(value)}
        </Text>
      ),
    },
  ];

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]}>
          <Select<number>
            placeholder="Choose a class"
            aria-label="Class"
            value={classId}
            loading={classesQuery.isLoading}
            status={classesQuery.isError ? 'error' : undefined}
            style={{ width: 220, maxWidth: '100%' }}
            options={(classesQuery.data ?? []).map((c) => ({ value: c.id, label: c.displayName }))}
            notFoundContent={<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No classes" />}
            onChange={setClassId}
          />
          <RangePicker
            aria-label="Date range"
            allowClear={false}
            format="DD MMM YYYY"
            value={[dayjs(from), dayjs(to)]}
            disabledDate={(day: Dayjs) => (toIsoDate(day) ?? '') > today}
            onChange={(days) => {
              const start = toIsoDate(days?.[0]);
              const end = toIsoDate(days?.[1]);
              if (start && end) setRange([start, end]);
            }}
          />
        </Space>
      </Card>

      {classesQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(classesQuery.error)}
          action={
            <Button size="small" onClick={() => classesQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : classId === undefined ? (
        <Card>
          <Empty description="Choose a class to see its report" />
        </Card>
      ) : reportQuery.isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : reportQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(reportQuery.error)}
          action={
            <Button size="small" onClick={() => reportQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : report ? (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Alert
            type={lowCount > 0 ? 'warning' : 'info'}
            showIcon
            message={`${report.daysTaken} days recorded. ${lowCount} student(s) below ${report.lowThreshold}%.`}
          />
          <Card styles={{ body: { padding: 0 } }} title={report.className}>
            <Table<StudentAttendance>
              rowKey="studentId"
              columns={columns}
              dataSource={report.students}
              pagination={false}
              scroll={{ x: 640 }}
              onRow={(row) => ({ style: row.low ? { background: colors.tintRose } : undefined })}
              locale={{ emptyText: <Empty description="No active students in this class" /> }}
            />
          </Card>
        </Space>
      ) : null}
    </div>
  );
}
