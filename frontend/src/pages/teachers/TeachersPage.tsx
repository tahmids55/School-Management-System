import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Dropdown,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { errorMessage, fieldErrors } from '../../api/client';
import {
  createTeacher,
  listTeachers,
  teacherKeys,
  updateTeacher,
  updateTeacherStatus,
  type CreatedTeacherResponse,
  type Teacher,
  type TeacherRequest,
  type TeacherStatus,
} from '../../api/teachers';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader, StatusTag, showTemporaryPassword } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { toIsoDate } from '../../utils/format';
import { schoolToday } from '../../utils/schoolDate';

const { Text } = Typography;

const PAGE_SIZE = 20;

const STATUS_OPTIONS: { value: TeacherStatus; label: string }[] = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'ON_LEAVE', label: 'On leave' },
  { value: 'RESIGNED', label: 'Resigned' },
];

const TEACHER_FORM_FIELDS: ReadonlySet<string> = new Set([
  'employeeNo',
  'firstName',
  'lastName',
  'email',
  'phone',
  'designation',
  'joiningDate',
]);

interface TeacherFormValues {
  employeeNo: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  designation: string;
  joiningDate: Dayjs;
}

export default function TeachersPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const queryClient = useQueryClient();

  const [searchInput, setSearchInput] = useState('');
  const [appliedQuery, setAppliedQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<TeacherStatus | undefined>(undefined);
  const [page, setPage] = useState(0);

  // undefined = modal closed, null = creating, Teacher = editing that teacher
  const [editing, setEditing] = useState<Teacher | null | undefined>(undefined);
  const [form] = Form.useForm<TeacherFormValues>();

  const queryParams = {
    q: appliedQuery || undefined,
    status: statusFilter,
    page,
    size: PAGE_SIZE,
  };

  const teachersQuery = useQuery({
    queryKey: teacherKeys.list(queryParams),
    queryFn: () => listTeachers(queryParams),
  });

  const refreshTeachers = () => queryClient.invalidateQueries({ queryKey: teacherKeys.all });

  const saveMutation = useMutation<CreatedTeacherResponse | Teacher, Error, TeacherFormValues>({
    mutationFn: (values: TeacherFormValues) => {
      const payload: TeacherRequest = {
        employeeNo: values.employeeNo.trim(),
        firstName: values.firstName.trim(),
        lastName: values.lastName.trim(),
        email: values.email.trim(),
        phone: values.phone.trim(),
        designation: values.designation.trim(),
        joiningDate: toIsoDate(values.joiningDate) ?? schoolToday(),
      };
      return editing ? updateTeacher(editing.id, payload) : createTeacher(payload);
    },
    onSuccess: (result) => {
      setEditing(undefined);
      form.resetFields();
      void refreshTeachers();
      if ('account' in result) {
        showTemporaryPassword(result.account.username, result.account.temporaryPassword);
      } else {
        feedback.message.success('Teacher updated');
      }
    },
    onError: (err) => {
      const fields = fieldErrors(err).filter(
        (field): field is { name: keyof TeacherFormValues; errors: string[] } =>
          TEACHER_FORM_FIELDS.has(field.name),
      );
      if (fields.length > 0) {
        form.setFields(fields);
      } else {
        feedback.message.error(errorMessage(err));
      }
    },
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, status }: { id: number; status: TeacherStatus }) =>
      updateTeacherStatus(id, status),
    onSuccess: () => {
      feedback.message.success('Status updated');
      void refreshTeachers();
    },
    onError: (err) => feedback.message.error(errorMessage(err)),
  });

  const applySearch = (nextSearch = searchInput) => {
    setAppliedQuery(nextSearch.trim());
    setPage(0);
  };

  const openCreateModal = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({
      joiningDate: dayjs(schoolToday()),
    });
  };

  const openEditModal = (teacher: Teacher) => {
    setEditing(teacher);
    form.resetFields();
    form.setFieldsValue({
      employeeNo: teacher.employeeNo,
      firstName: teacher.firstName,
      lastName: teacher.lastName,
      email: teacher.email,
      phone: teacher.phone,
      designation: teacher.designation,
      joiningDate: dayjs(teacher.joiningDate),
    });
  };

  const closeModal = () => {
    if (saveMutation.isPending) return;
    setEditing(undefined);
    form.resetFields();
  };

  const handleStatusChange = (teacher: Teacher, status: TeacherStatus) => {
    if (status === 'RESIGNED') {
      feedback.modal.confirm({
        title: `Mark ${teacher.fullName} as resigned?`,
        content: 'Their login account will be disabled immediately. Past records will be kept.',
        okText: 'Confirm',
        cancelText: 'Cancel',
        okButtonProps: { danger: true },
        onOk: () => statusMutation.mutateAsync({ id: teacher.id, status }),
      });
      return;
    }
    statusMutation.mutate({ id: teacher.id, status });
  };

  const statusMenuItems = (teacher: Teacher): MenuProps['items'] => [
    {
      key: 'ACTIVE',
      label: 'Set active',
      disabled: teacher.status === 'ACTIVE',
      onClick: () => handleStatusChange(teacher, 'ACTIVE'),
    },
    {
      key: 'ON_LEAVE',
      label: 'Set on leave',
      disabled: teacher.status === 'ON_LEAVE',
      onClick: () => handleStatusChange(teacher, 'ON_LEAVE'),
    },
    { type: 'divider' },
    {
      key: 'RESIGNED',
      label: 'Mark resigned',
      danger: true,
      disabled: teacher.status === 'RESIGNED',
      onClick: () => handleStatusChange(teacher, 'RESIGNED'),
    },
  ];

  const columns: ColumnsType<Teacher> = [
    {
      title: 'Employee no.',
      dataIndex: 'employeeNo',
      key: 'employeeNo',
      width: 130,
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
      render: (value: string, row) => (
        <div>
          <Text strong>{value}</Text>
          {row.username && (
            <div>
              <Text type="secondary" style={{ fontSize: 12 }}>
                @{row.username}
              </Text>
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Designation',
      dataIndex: 'designation',
      key: 'designation',
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Phone',
      dataIndex: 'phone',
      key: 'phone',
      render: (value: string) => <Text style={{ whiteSpace: 'nowrap' }}>{value}</Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: TeacherStatus) => <StatusTag value={status} />,
    },
  ];

  if (isAdmin) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      align: 'right',
      width: 170,
      render: (_, teacher) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => openEditModal(teacher)}>
            Edit
          </Button>
          <Dropdown menu={{ items: statusMenuItems(teacher) }} trigger={['click']}>
            <Button
              size="small"
              loading={statusMutation.isPending && statusMutation.variables?.id === teacher.id}
            >
              Actions <DownOutlined />
            </Button>
          </Dropdown>
        </Space>
      ),
    });
  }

  const rows = teachersQuery.data?.content ?? [];
  const total = teachersQuery.data?.totalElements ?? 0;

  return (
    <div>
      <PageHeader
        title="Teachers"
        subtitle="Staff directory and teaching assignments"
        actions={
          isAdmin ? (
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              Add teacher
            </Button>
          ) : undefined
        }
      />

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Input
            placeholder="Name, employee no. or email"
            aria-label="Name, employee no. or email"
            value={searchInput}
            allowClear
            style={{ width: 260, maxWidth: '100%' }}
            onChange={(e) => {
              const value = e.target.value;
              setSearchInput(value);
              if (value === '') applySearch('');
            }}
            onPressEnter={() => applySearch()}
          />

          <Select<TeacherStatus>
            placeholder="Any status"
            aria-label="Any status"
            allowClear
            value={statusFilter}
            options={STATUS_OPTIONS}
            style={{ width: 170, maxWidth: '100%' }}
            onChange={(value) => {
              setStatusFilter(value);
              setPage(0);
            }}
          />

          <Button type="primary" icon={<SearchOutlined />} onClick={() => applySearch()}>
            Filter
          </Button>
        </Space>
      </Card>

      {teachersQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(teachersQuery.error)}
          action={
            <Button size="small" onClick={() => teachersQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<Teacher>
            rowKey="id"
            columns={columns}
            dataSource={rows}
            loading={teachersQuery.isLoading || teachersQuery.isFetching}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No teachers found" /> }}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total,
              showSizeChanger: false,
              onChange: (nextPage) => setPage(nextPage - 1),
            }}
          />
        </Card>
      )}

      <Modal
        title={editing ? 'Edit' : 'Add teacher'}
        open={editing !== undefined}
        onOk={() => form.submit()}
        onCancel={closeModal}
        okText="Save"
        cancelText="Cancel"
        confirmLoading={saveMutation.isPending}
        forceRender
        style={{ maxWidth: 'calc(100vw - 32px)' }}
      >
        <Form<TeacherFormValues>
          form={form}
          layout="vertical"
          style={{ marginTop: 16 }}
          onFinish={(values) => saveMutation.mutate(values)}
        >
          <Space wrap size="middle" style={{ width: '100%' }}>
            <Form.Item
              name="employeeNo"
              label="Employee no."
              style={{ flex: 1, minWidth: 160 }}
              rules={[
                { required: true, whitespace: true, message: 'Employee no. is required' },
                { max: 20, message: 'At most 20 characters' },
              ]}
            >
              <Input placeholder="T-1007" disabled={Boolean(editing)} maxLength={20} />
            </Form.Item>

            <Form.Item
              name="joiningDate"
              label="Joining date"
              style={{ flex: 1, minWidth: 160 }}
              rules={[{ required: true, message: 'Joining date is required' }]}
            >
              <DatePicker
                style={{ width: '100%' }}
                format="YYYY-MM-DD"
                disabledDate={(date) => date.isAfter(dayjs(schoolToday()), 'day')}
              />
            </Form.Item>
          </Space>

          <Space wrap size="middle" style={{ width: '100%' }}>
            <Form.Item
              name="firstName"
              label="First name"
              style={{ flex: 1, minWidth: 160 }}
              rules={[
                { required: true, whitespace: true, message: 'First name is required' },
                { max: 60, message: 'At most 60 characters' },
              ]}
            >
              <Input maxLength={60} />
            </Form.Item>

            <Form.Item
              name="lastName"
              label="Last name"
              style={{ flex: 1, minWidth: 160 }}
              rules={[
                { required: true, whitespace: true, message: 'Last name is required' },
                { max: 60, message: 'At most 60 characters' },
              ]}
            >
              <Input maxLength={60} />
            </Form.Item>
          </Space>

          <Form.Item
            name="designation"
            label="Designation"
            rules={[
              { required: true, whitespace: true, message: 'Designation is required' },
              { max: 60, message: 'At most 60 characters' },
            ]}
          >
            <Input maxLength={60} />
          </Form.Item>

          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, whitespace: true, message: 'Email is required' },
              { type: 'email', message: 'Enter a valid email address' },
              { max: 120, message: 'At most 120 characters' },
            ]}
          >
            <Input maxLength={120} />
          </Form.Item>

          <Form.Item
            name="phone"
            label="Phone"
            extra="10-15 digits, optional +"
            rules={[
              { required: true, whitespace: true, message: 'Phone is required' },
              { pattern: /^\+?[0-9]{10,15}$/, message: '10-15 digits, optional +' },
            ]}
          >
            <Input placeholder="+8801..." maxLength={20} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
