import { useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Empty,
  InputNumber,
  Select,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { SaveOutlined } from '@ant-design/icons';
import { classKeys, listClasses, listClassSubjects } from '../../api/classes';
import { errorMessage } from '../../api/client';
import {
  getMarkSheet,
  listExams,
  saveMarkSheet,
  type MarkEntry,
  type MarkRow,
} from '../../api/exams';
import { getMyTeachingLoad, teacherKeys } from '../../api/teachers';
import { useAuth } from '../../auth/AuthContext';
import { feedback } from '../../utils/feedback';

const { Text } = Typography;

interface EditableRow {
  studentId: number;
  rollNo?: number | null;
  admissionNo: string;
  studentName: string;
  marksObtained: number | null;
  absent: boolean;
}

export default function MarkEntryTab() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const queryClient = useQueryClient();

  const [examId, setExamId] = useState<number | undefined>(undefined);
  const [classId, setClassId] = useState<number | undefined>(undefined);
  const [subjectId, setSubjectId] = useState<number | undefined>(undefined);
  const [draftRows, setDraftRows] = useState<EditableRow[]>([]);

  const examsQuery = useQuery({
    queryKey: ['exams', 'current'],
    queryFn: () => listExams(),
  });

  const selectedExam = useMemo(
    () => (examsQuery.data ?? []).find((e) => e.id === examId),
    [examsQuery.data, examId],
  );

  // Admin loads classes & class-subjects for the exam's academic year
  const adminClassesQuery = useQuery({
    queryKey: classKeys.list(selectedExam?.academicYearId),
    queryFn: () => listClasses(selectedExam?.academicYearId),
    enabled: isAdmin && selectedExam !== undefined,
  });

  const adminSubjectsQuery = useQuery({
    queryKey: classKeys.subjects(classId ?? 0),
    queryFn: () => listClassSubjects(classId!),
    enabled: isAdmin && classId !== undefined,
  });

  // Teacher loads only their assigned (class, subject) pairs
  const teacherLoadQuery = useQuery({
    queryKey: teacherKeys.myTeachingLoad,
    queryFn: getMyTeachingLoad,
    enabled: !isAdmin,
  });

  const teacherAssignmentsForYear = useMemo(() => {
    if (isAdmin || !selectedExam) return [];
    return (teacherLoadQuery.data?.subjects ?? []).filter(
      (a) => a.academicYear === selectedExam.academicYearName,
    );
  }, [isAdmin, selectedExam, teacherLoadQuery.data]);

  const classOptions = useMemo(() => {
    if (isAdmin) {
      return (adminClassesQuery.data ?? []).map((c) => ({
        value: c.id,
        label: c.displayName,
      }));
    }
    const unique = new Map<number, string>();
    for (const item of teacherAssignmentsForYear) {
      unique.set(item.classId, item.className);
    }
    return Array.from(unique.entries()).map(([value, label]) => ({ value, label }));
  }, [isAdmin, adminClassesQuery.data, teacherAssignmentsForYear]);

  const subjectOptions = useMemo(() => {
    if (!classId) return [];
    if (isAdmin) {
      return (adminSubjectsQuery.data ?? []).map((s) => ({
        value: s.subjectId,
        label: `${s.subjectCode} – ${s.subjectName}`,
      }));
    }
    return teacherAssignmentsForYear
      .filter((a) => a.classId === classId)
      .map((a) => ({
        value: a.subjectId,
        label: `${a.subjectCode} – ${a.subjectName}`,
      }));
  }, [isAdmin, classId, adminSubjectsQuery.data, teacherAssignmentsForYear]);

  const sheetQuery = useQuery({
    queryKey: ['mark-sheet', examId, classId, subjectId],
    queryFn: () => getMarkSheet(examId!, classId!, subjectId!),
    enabled: examId !== undefined && classId !== undefined && subjectId !== undefined,
  });

  useEffect(() => {
    if (!sheetQuery.data) {
      setDraftRows([]);
      return;
    }
    setDraftRows(
      sheetQuery.data.rows.map((r: MarkRow) => ({
        studentId: r.studentId,
        rollNo: r.rollNo,
        admissionNo: r.admissionNo,
        studentName: r.studentName,
        marksObtained: r.absent ? null : (r.marksObtained ?? null),
        absent: r.absent,
      })),
    );
  }, [sheetQuery.data]);

  const saveMutation = useMutation({
    mutationFn: (entries: MarkEntry[]) =>
      saveMarkSheet(examId!, { classId: classId!, subjectId: subjectId!, entries }),
    onSuccess: (updated) => {
      feedback.message.success('Marks saved');
      queryClient.setQueryData(['mark-sheet', examId, classId, subjectId], updated);
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const updateRow = (studentId: number, patch: Partial<Pick<EditableRow, 'marksObtained' | 'absent'>>) => {
    setDraftRows((prev) =>
      prev.map((row) => {
        if (row.studentId !== studentId) return row;
        const nextAbsent = patch.absent ?? row.absent;
        return {
          ...row,
          absent: nextAbsent,
          marksObtained: nextAbsent ? null : (patch.marksObtained !== undefined ? patch.marksObtained : row.marksObtained),
        };
      }),
    );
  };

  const handleSave = () => {
    const entries: MarkEntry[] = draftRows.map((row) => ({
      studentId: row.studentId,
      marksObtained: row.absent ? null : row.marksObtained,
      absent: row.absent,
    }));
    saveMutation.mutate(entries);
  };

  const sheet = sheetQuery.data;
  const editable = sheet?.editable ?? false;

  const columns: ColumnsType<EditableRow> = [
    {
      title: 'Roll',
      dataIndex: 'rollNo',
      key: 'rollNo',
      width: 80,
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
      title: sheet ? `Marks (/${sheet.fullMarks})` : 'Marks',
      key: 'marksObtained',
      width: 170,
      render: (_, row) => (
        <InputNumber
          min={0}
          max={sheet?.fullMarks ?? 100}
          step={0.5}
          precision={2}
          disabled={!editable || row.absent}
          value={row.absent ? null : row.marksObtained}
          style={{ width: 120 }}
          onChange={(val) => updateRow(row.studentId, { marksObtained: val === null ? null : Number(val) })}
        />
      ),
    },
    {
      title: 'Absent',
      key: 'absent',
      width: 90,
      align: 'center',
      render: (_, row) => (
        <Checkbox
          checked={row.absent}
          disabled={!editable}
          onChange={(e) => updateRow(row.studentId, { absent: e.target.checked })}
        />
      ),
    },
  ];

  const allSelected = examId !== undefined && classId !== undefined && subjectId !== undefined;

  return (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Select<number>
            placeholder="Exam"
            aria-label="Exam"
            value={examId}
            loading={examsQuery.isLoading}
            style={{ width: 220, maxWidth: '100%' }}
            options={(examsQuery.data ?? []).map((e) => ({
              value: e.id,
              label: `${e.name} (${e.academicYearName})`,
            }))}
            onChange={(value) => {
              setExamId(value);
              setClassId(undefined);
              setSubjectId(undefined);
            }}
          />

          <Select<number>
            placeholder="Class"
            aria-label="Class"
            value={classId}
            disabled={!examId}
            loading={isAdmin ? adminClassesQuery.isLoading : teacherLoadQuery.isLoading}
            style={{ width: 200, maxWidth: '100%' }}
            options={classOptions}
            onChange={(value) => {
              setClassId(value);
              setSubjectId(undefined);
            }}
          />

          <Select<number>
            placeholder="Subject"
            aria-label="Subject"
            value={subjectId}
            disabled={!classId}
            loading={isAdmin ? adminSubjectsQuery.isLoading : teacherLoadQuery.isLoading}
            style={{ width: 240, maxWidth: '100%' }}
            options={subjectOptions}
            onChange={(value) => setSubjectId(value)}
          />
        </Space>
      </Card>

      {!allSelected ? (
        <Card>
          <Empty description="Choose an exam, class and subject to enter marks" />
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
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          {!sheet.editable && (
            <Alert
              type="warning"
              showIcon
              message="Results of this exam are published, so marks are locked. An admin can unpublish to allow changes."
            />
          )}

          <Card
            styles={{ body: { padding: 0 } }}
            title={`${sheet.className} · ${sheet.subjectCode} ${sheet.subjectName}`}
            extra={
              <Button
                type="primary"
                icon={<SaveOutlined />}
                disabled={!editable || draftRows.length === 0}
                loading={saveMutation.isPending}
                onClick={handleSave}
              >
                Save marks
              </Button>
            }
          >
            <Table<EditableRow>
              rowKey="studentId"
              columns={columns}
              dataSource={draftRows}
              pagination={false}
              scroll={{ x: 540 }}
              locale={{
                emptyText: <Empty description="No active students in this class" />,
              }}
            />
          </Card>
        </Space>
      ) : null}
    </div>
  );
}
