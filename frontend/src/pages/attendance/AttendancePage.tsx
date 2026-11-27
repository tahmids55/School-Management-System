import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Input,
  Radio,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckOutlined, SaveOutlined } from '@ant-design/icons';
import {
  attendanceKeys,
  getAttendanceSheet,
  saveAttendanceSheet,
  type AttendanceStatus,
  type Entry,
  type SheetRow,
} from '../../api/attendance';
import { errorMessage } from '../../api/client';
import { PageHeader } from '../../components/common';
import { useMyClasses } from '../../hooks/useMyClasses';
import { feedback } from '../../utils/feedback';
import { toIsoDate } from '../../utils/format';
import { schoolToday } from '../../utils/schoolDate';
import AttendanceReportTab from './AttendanceReportTab';

const { Text } = Typography;

const STATUS_OPTIONS: { value: AttendanceStatus; label: string }[] = [
  { value: 'PRESENT', label: 'Present' },
  { value: 'LATE', label: 'Late' },
  { value: 'ABSENT', label: 'Absent' },
  { value: 'EXCUSED', label: 'Excused' },
];

/** One row while the teacher is editing; status stays null until chosen. */
interface DraftRow {
  studentId: number;
  rollNo?: number | null;
  admissionNo: string;
  studentName: string;
  status: AttendanceStatus | null;
  remarks: string;
}

function toDraft(row: SheetRow): DraftRow {
  return {
    studentId: row.studentId,
    rollNo: row.rollNo,
    admissionNo: row.admissionNo,
    studentName: row.studentName,
    status: row.status ?? null,
    remarks: row.remarks ?? '',
  };
}

export default function AttendancePage() {
  return (
    <div>
      <PageHeader title="Attendance" subtitle="Take daily attendance and review attendance reports" />
      <Tabs
        defaultActiveKey="take"
        items={[
          { key: 'take', label: 'Take attendance', children: <TakeAttendanceTab /> },
          { key: 'report', label: 'Report', children: <AttendanceReportTab /> },
        ]}
      />
    </div>
  );
}

function TakeAttendanceTab() {
  const queryClient = useQueryClient();
  // "Today" is the school's day in Dhaka, not the browser's day.
  const today = schoolToday();
  // The dashboard's class cards open this tab with ?classId=, so that class is chosen already.
  const [searchParams] = useSearchParams();
  const linkedClassId = Number(searchParams.get('classId')) || undefined;
  const [classId, setClassId] = useState<number | undefined>(linkedClassId);
  const [date, setDate] = useState<string>(today);
  const [rows, setRows] = useState<DraftRow[]>([]);

  const classesQuery = useMyClasses();

  const sheetQuery = useQuery({
    queryKey: attendanceKeys.sheet(classId ?? 0, date),
    queryFn: () => getAttendanceSheet(classId!, date),
    enabled: classId !== undefined,
  });

  // Each time a sheet arrives (other class, other day, or after saving) the edit rows start from it.
  useEffect(() => {
    setRows(sheetQuery.data ? sheetQuery.data.rows.map(toDraft) : []);
  }, [sheetQuery.data]);

  const saveMutation = useMutation({
    mutationFn: (entries: Entry[]) => saveAttendanceSheet({ classId: classId!, date, entries }),
    onSuccess: (saved) => {
      feedback.message.success('Attendance saved');
      queryClient.setQueryData(attendanceKeys.sheet(saved.classId, saved.date), saved);
      // Reports and student summaries now show old numbers: fetch them again when next shown.
      void queryClient.invalidateQueries({ queryKey: [...attendanceKeys.all, 'report'] });
      void queryClient.invalidateQueries({ queryKey: [...attendanceKeys.all, 'student'] });
    },
    onError: (err) => feedback.message.error(errorMessage(err)),
  });

  const updateRow = (studentId: number, patch: Partial<Pick<DraftRow, 'status' | 'remarks'>>) => {
    setRows((prev) => prev.map((row) => (row.studentId === studentId ? { ...row, ...patch } : row)));
  };

  const markAllPresent = () => {
    setRows((prev) => prev.map((row) => ({ ...row, status: 'PRESENT' })));
  };

  const allMarked = rows.length > 0 && rows.every((row) => row.status !== null);

  const handleSave = () => {
    const entries: Entry[] = rows.map((row) => ({
      studentId: row.studentId,
      status: row.status!,
      remarks: row.remarks.trim() || null,
    }));
    saveMutation.mutate(entries);
  };

  const columns: ColumnsType<DraftRow> = [
    {
      title: 'Roll',
      dataIndex: 'rollNo',
      key: 'rollNo',
      width: 70,
      render: (value?: number | null) => value ?? '-',
    },
    {
      title: 'Student',
      key: 'student',
      render: (_, row) => (
        <div>
          <Text strong>{row.studentName}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {row.admissionNo}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Status',
      key: 'status',
      width: 300,
      render: (_, row) => (
        <Radio.Group
          size="small"
          optionType="button"
          buttonStyle="solid"
          options={STATUS_OPTIONS}
          value={row.status}
          onChange={(e) => updateRow(row.studentId, { status: e.target.value as AttendanceStatus })}
        />
      ),
    },
    {
      title: 'Remarks',
      key: 'remarks',
      width: 220,
      render: (_, row) => (
        <Input
          size="small"
          placeholder="Optional"
          maxLength={255}
          value={row.remarks}
          onChange={(e) => updateRow(row.studentId, { remarks: e.target.value })}
        />
      ),
    },
  ];

  const sheet = sheetQuery.data;

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
          <DatePicker
            aria-label="Date"
            allowClear={false}
            format="DD MMM YYYY"
            value={dayjs(date)}
            disabledDate={(day: Dayjs) => (toIsoDate(day) ?? '') > today}
            onChange={(day) => setDate(toIsoDate(day) ?? today)}
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
          <Empty description="Choose a class to take attendance" />
        </Card>
      ) : sheetQuery.isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : sheetQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(sheetQuery.error)}
          action={
            <Button size="small" onClick={() => sheetQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : sheet ? (
        <Card
          styles={{ body: { padding: 0 } }}
          title={
            <Space wrap size={8}>
              <span>{sheet.className}</span>
              {sheet.taken ? (
                <Tag color="green" bordered={false}>
                  Already taken - you can correct it
                </Tag>
              ) : (
                <Tag bordered={false}>Not taken yet</Tag>
              )}
            </Space>
          }
        >
          <Space wrap size={8} style={{ padding: 16 }}>
            <Button icon={<CheckOutlined />} disabled={rows.length === 0} onClick={markAllPresent}>
              Mark all present
            </Button>
            <Button
              type="primary"
              icon={<SaveOutlined />}
              disabled={!allMarked}
              loading={saveMutation.isPending}
              onClick={handleSave}
            >
              Save attendance
            </Button>
          </Space>
          <Table<DraftRow>
            rowKey="studentId"
            columns={columns}
            dataSource={rows}
            pagination={false}
            scroll={{ x: 760 }}
            locale={{ emptyText: <Empty description="No active students in this class" /> }}
          />
        </Card>
      ) : null}
    </div>
  );
}
