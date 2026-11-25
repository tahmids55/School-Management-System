import { useNavigate } from 'react-router-dom';
import { Alert, Button, Empty, Table, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useQuery } from '@tanstack/react-query';
import type { SchoolClass } from '../../api/classes';
import { errorMessage } from '../../api/client';
import { listClassStudents, studentKeys, type StudentSummary } from '../../api/students';
import { humanize } from '../../utils/format';

const { Text } = Typography;

/** The active students of one class, ordered by roll (F13). A row opens the student's profile. */
export default function ClassStudentsTab({ schoolClass }: { schoolClass: SchoolClass }) {
  const navigate = useNavigate();
  const roster = useQuery({
    queryKey: studentKeys.roster(schoolClass.id),
    queryFn: () => listClassStudents(schoolClass.id),
  });

  if (roster.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={errorMessage(roster.error)}
        action={<Button onClick={() => void roster.refetch()}>Try again</Button>}
      />
    );
  }

  const columns: ColumnsType<StudentSummary> = [
    { title: 'Roll', dataIndex: 'rollNo', width: 70, align: 'right' },
    { title: 'Name', dataIndex: 'fullName', render: (name: string) => <Text strong>{name}</Text> },
    { title: 'Admission no.', dataIndex: 'admissionNo' },
    { title: 'Gender', dataIndex: 'gender', render: (gender: string) => humanize(gender) },
    { title: 'Guardian phone', dataIndex: 'guardianPhone' },
  ];

  return (
    <Table<StudentSummary>
      rowKey="id"
      size="small"
      columns={columns}
      dataSource={roster.data ?? []}
      loading={roster.isLoading}
      pagination={false}
      scroll={{ x: 'max-content' }}
      locale={{ emptyText: <Empty description={`No students in ${schoolClass.displayName} yet`} /> }}
      onRow={(student) => ({
        onClick: () => navigate(`/students/${student.id}`),
        style: { cursor: 'pointer' },
      })}
    />
  );
}
