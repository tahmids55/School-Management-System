import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Alert, Breadcrumb, Button, Card, Col, Descriptions, Result, Row, Select, Spin, Tabs, Typography } from 'antd';
import { EditOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import axios from 'axios';
import { errorMessage } from '../../api/client';
import { useAuth } from '../../auth/AuthContext';
import { classKeys } from '../../api/classes';
import {
  changeStudentStatus,
  getStudent,
  STUDENT_STATUSES,
  studentKeys,
  type Student,
  type StudentStatus,
} from '../../api/students';
import { PageHeader, StatCard, StatusTag } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { useStudentAttendanceThisYear } from '../../hooks/useStudentAttendance';
import { formatDate, formatPercent, humanize } from '../../utils/format';
import StudentAttendanceTab from './StudentAttendanceTab';
import StudentFeesTab from './StudentFeesTab';
import StudentFormDrawer from './StudentFormDrawer';

const { Text } = Typography;

/** /students/:id: header with status, stat cards, and the tabs Profile | Attendance (F20) | Fees (F21). */
export default function StudentDetailPage() {
  const id = Number(useParams().id);
  const validId = Number.isInteger(id) && id > 0;
  const queryClient = useQueryClient();
  const isAdmin = useAuth().hasRole('ADMIN');
  const [editing, setEditing] = useState(false);

  const student = useQuery({
    queryKey: studentKeys.detail(id),
    queryFn: () => getStudent(id),
    enabled: validId,
  });
  // Same cached query as the Attendance tab below (F20).
  const attendance = useStudentAttendanceThisYear(validId ? id : undefined);

  const status = useMutation({
    mutationFn: (next: StudentStatus) => changeStudentStatus(id, next),
    onSuccess: (saved) => {
      feedback.message.success('Status updated');
      queryClient.setQueryData(studentKeys.detail(id), saved);
      void queryClient.invalidateQueries({ queryKey: studentKeys.all });
      void queryClient.invalidateQueries({ queryKey: classKeys.all });
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  function confirmStatus(current: Student, next: StudentStatus) {
    feedback.modal.confirm({
      title: `Change ${current.fullName} to ${humanize(next)}?`,
      content:
        next === 'ACTIVE'
          ? 'The student takes a seat in their class again.'
          : 'The record is kept, the class seat is freed and the student can no longer log in.',
      okText: 'Change status',
      onOk: () => status.mutateAsync(next).catch(() => undefined),
    });
  }

  // 403 (not your class) is shown like 404: the page does not reveal that the student exists.
  const notFound =
    !validId ||
    (axios.isAxiosError(student.error) && [403, 404].includes(student.error.response?.status ?? 0));
  if (notFound) {
    return (
      <Result
        status="404"
        title="Student not found"
        extra={
          <Link to="/students">
            <Button type="primary">Students</Button>
          </Link>
        }
      />
    );
  }
  if (student.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={errorMessage(student.error)}
        action={<Button onClick={() => void student.refetch()}>Try again</Button>}
      />
    );
  }
  if (!student.data) {
    return (
      <div className="full-page-center">
        <Spin size="large" />
      </div>
    );
  }

  const s = student.data;
  const placement = s.className ? `${s.className}${s.rollNo ? ` · Roll ${s.rollNo}` : ''}` : 'Unassigned';

  return (
    <div>
      <Breadcrumb
        style={{ marginBottom: 8 }}
        items={[{ title: <Link to="/students">Students</Link> }, { title: s.fullName }]}
      />
      <PageHeader
        title={s.fullName}
        subtitle={`${s.admissionNo} · ${placement}`}
        actions={
          <>
            <StatusTag value={s.status} />
            {isAdmin && (
              <>
                <Select
                  aria-label="Status"
                  value={s.status}
                  loading={status.isPending}
                  style={{ width: 150 }}
                  options={STUDENT_STATUSES.map((value) => ({ value, label: humanize(value) }))}
                  onChange={(next: StudentStatus) => confirmStatus(s, next)}
                />
                <Button icon={<EditOutlined />} onClick={() => setEditing(true)}>
                  Edit
                </Button>
              </>
            )}
          </>
        }
      />

      {/* Attendance from F20; the fees value comes with F21. */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <StatCard
            label="Attendance this year"
            value={attendance.data ? formatPercent(attendance.data.percentage) : '-'}
            tint={attendance.data?.low ? 'rose' : 'mint'}
          />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard
            label="Days present / absent"
            value={attendance.data ? `${attendance.data.present} / ${attendance.data.absent}` : '- / -'}
            tint="sky"
          />
        </Col>
        <Col xs={24} sm={8}>
          <StatCard label="Fees outstanding" value="-" tint="peach" />
        </Col>
      </Row>

      <Card>
        <Tabs
          items={[
            {
              key: 'profile',
              label: 'Profile',
              children: (
                <Descriptions column={{ xs: 1, sm: 2 }} size="small">
                  <Descriptions.Item label="Gender">{humanize(s.gender)}</Descriptions.Item>
                  <Descriptions.Item label="Date of birth">{formatDate(s.dateOfBirth)}</Descriptions.Item>
                  <Descriptions.Item label="Email">{s.email || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Phone">{s.phone || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Address">{s.address || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Guardian">{`${s.guardianName} (${s.guardianRelation})`}</Descriptions.Item>
                  <Descriptions.Item label="Guardian phone">{s.guardianPhone}</Descriptions.Item>
                  <Descriptions.Item label="Admission date">{formatDate(s.admissionDate)}</Descriptions.Item>
                  <Descriptions.Item label="Login">
                    {s.username ?? <Text type="secondary">No login</Text>}
                  </Descriptions.Item>
                </Descriptions>
              ),
            },
            { key: 'attendance', label: 'Attendance', children: <StudentAttendanceTab studentId={s.id} /> },
            { key: 'fees', label: 'Fees', children: <StudentFeesTab studentId={s.id} /> },
          ]}
        />
      </Card>

      <StudentFormDrawer
        open={editing}
        student={s}
        onClose={() => setEditing(false)}
        onSaved={(saved) => queryClient.setQueryData(studentKeys.detail(id), saved)}
      />
    </div>
  );
}
