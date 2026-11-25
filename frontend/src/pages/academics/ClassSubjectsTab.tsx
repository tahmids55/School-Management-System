import { useState } from 'react';
import { Alert, Button, Empty, Popconfirm, Select, Space, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  assignClassSubject,
  classKeys,
  listClassSubjects,
  removeClassSubject,
  type ClassSubject,
  type ClassSubjectRequest,
  type SchoolClass,
} from '../../api/classes';
import { errorMessage } from '../../api/client';
import { teacherKeys } from '../../api/teachers';
import { useAuth } from '../../auth/AuthContext';
import { useActiveTeachers } from '../../hooks/useActiveTeachers';
import { useSubjects } from '../../hooks/useSubjects';
import { feedback } from '../../utils/feedback';

const { Text } = Typography;

/** Subjects and their assigned teachers for one class. */
export default function ClassSubjectsTab({ schoolClass }: { schoolClass: SchoolClass }) {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const queryClient = useQueryClient();

  const [newSubjectId, setNewSubjectId] = useState<number>();
  const [newTeacherId, setNewTeacherId] = useState<number>();

  const assignmentsQuery = useQuery({
    queryKey: classKeys.subjects(schoolClass.id),
    queryFn: () => listClassSubjects(schoolClass.id),
  });
  const subjectsQuery = useSubjects();
  const teachersQuery = useActiveTeachers(isAdmin);

  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: classKeys.subjects(schoolClass.id) }),
      queryClient.invalidateQueries({ queryKey: teacherKeys.all }),
    ]);

  const assignMutation = useMutation({
    mutationFn: (payload: ClassSubjectRequest) => assignClassSubject(schoolClass.id, payload),
    onSuccess: (_, payload) => {
      feedback.message.success('Assignment saved');
      if (payload.subjectId === newSubjectId) {
        setNewSubjectId(undefined);
        setNewTeacherId(undefined);
      }
      void refresh();
    },
    onError: (err) => feedback.message.error(errorMessage(err)),
  });

  const removeMutation = useMutation({
    mutationFn: (subjectId: number) => removeClassSubject(schoolClass.id, subjectId),
    onSuccess: () => {
      feedback.message.success('Assignment saved');
      void refresh();
    },
    onError: (err) => feedback.message.error(errorMessage(err)),
  });

  const assignments = assignmentsQuery.data ?? [];
  const assignedSubjectIds = new Set(assignments.map((item) => item.subjectId));

  const availableSubjects = (subjectsQuery.data ?? [])
    .filter((subject) => !assignedSubjectIds.has(subject.id))
    .map((subject) => ({
      value: subject.id,
      label: `${subject.code} — ${subject.name}`,
    }));

  const baseTeacherOptions = (teachersQuery.data ?? []).map((teacher) => ({
    value: teacher.id,
    label: `${teacher.fullName} (${teacher.employeeNo})`,
  }));

  const teacherOptionsForRow = (row: ClassSubject) => {
    if (
      row.teacherId != null &&
      row.teacherName &&
      !baseTeacherOptions.some((option) => option.value === row.teacherId)
    ) {
      return [{ value: row.teacherId, label: row.teacherName }, ...baseTeacherOptions];
    }
    return baseTeacherOptions;
  };

  const columns: ColumnsType<ClassSubject> = [
    {
      title: 'Subject',
      key: 'subject',
      render: (_, row) => (
        <div>
          <Text strong>{row.subjectCode}</Text>{' '}
          <span>{row.subjectName}</span>
        </div>
      ),
    },
    {
      title: 'Teacher',
      key: 'teacher',
      width: 240,
      render: (_, row) =>
        isAdmin ? (
          <Select<number | null>
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Not assigned"
            aria-label="Teacher"
            value={row.teacherId ?? undefined}
            loading={
              teachersQuery.isLoading ||
              (assignMutation.isPending && assignMutation.variables?.subjectId === row.subjectId)
            }
            options={teacherOptionsForRow(row)}
            style={{ width: '100%', minWidth: 180 }}
            onChange={(value) =>
              assignMutation.mutate({
                subjectId: row.subjectId,
                teacherId: value ?? null,
              })
            }
          />
        ) : row.teacherName ? (
          row.teacherName
        ) : (
          <Text type="secondary">Not assigned</Text>
        ),
    },
  ];

  if (isAdmin) {
    columns.push({
      key: 'actions',
      align: 'right',
      width: 90,
      render: (_, row) => (
        <Popconfirm
          title="Remove this subject from the class?"
          okText="Remove"
          cancelText="Cancel"
          okButtonProps={{ danger: true }}
          onConfirm={() => removeMutation.mutateAsync(row.subjectId).catch(() => undefined)}
        >
          <Button
            type="link"
            size="small"
            danger
            loading={removeMutation.isPending && removeMutation.variables === row.subjectId}
          >
            Remove
          </Button>
        </Popconfirm>
      ),
    });
  }

  if (assignmentsQuery.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={errorMessage(assignmentsQuery.error)}
        action={
          <Button size="small" onClick={() => void assignmentsQuery.refetch()}>
            Try again
          </Button>
        }
      />
    );
  }

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      {isAdmin && (
        <Space wrap size="small" style={{ width: '100%' }}>
          <Select<number>
            showSearch
            optionFilterProp="label"
            placeholder="Subject"
            aria-label="Subject"
            value={newSubjectId}
            loading={subjectsQuery.isLoading}
            options={availableSubjects}
            style={{ minWidth: 200, maxWidth: '100%' }}
            onChange={setNewSubjectId}
          />
          <Select<number>
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="Teacher"
            aria-label="Teacher"
            value={newTeacherId}
            loading={teachersQuery.isLoading}
            options={baseTeacherOptions}
            style={{ minWidth: 200, maxWidth: '100%' }}
            onChange={setNewTeacherId}
          />
          <Button
            type="primary"
            icon={<PlusOutlined />}
            disabled={newSubjectId === undefined}
            loading={assignMutation.isPending && assignMutation.variables?.subjectId === newSubjectId}
            onClick={() => {
              if (newSubjectId !== undefined) {
                assignMutation.mutate({
                  subjectId: newSubjectId,
                  teacherId: newTeacherId ?? null,
                });
              }
            }}
          >
            Add
          </Button>
        </Space>
      )}

      <Table<ClassSubject>
        rowKey="id"
        columns={columns}
        dataSource={assignments}
        loading={assignmentsQuery.isLoading}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{
          emptyText: (
            <Empty
              description={
                isAdmin ? 'Add subject assignments for this class' : 'No subjects assigned to this class yet'
              }
            />
          ),
        }}
      />
    </Space>
  );
}
