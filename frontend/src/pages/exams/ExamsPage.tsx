import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckCircleOutlined, DeleteOutlined, EditOutlined, PlusOutlined, RollbackOutlined } from '@ant-design/icons';
import { academicKeys, listAcademicYears, type AcademicYear } from '../../api/academics';
import { errorMessage, fieldErrors } from '../../api/client';
import {
  createExam,
  deleteExam,
  listExams,
  publishExam,
  unpublishExam,
  updateExam,
  type ExamRequest,
  type ExamResponse,
} from '../../api/exams';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader, StatusTag } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { formatDate, formatDateTime, toIsoDate } from '../../utils/format';
import MarkEntryTab from './MarkEntryTab';

const { Text } = Typography;
const { RangePicker } = DatePicker;

interface ExamFormValues {
  name: string;
  dates: [Dayjs, Dayjs];
}

export default function ExamsPage() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole('ADMIN');
  const queryClient = useQueryClient();

  const [selectedYearId, setSelectedYearId] = useState<number | undefined>(undefined);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<ExamResponse | null>(null);
  const [form] = Form.useForm<ExamFormValues>();

  const yearsQuery = useQuery({
    queryKey: academicKeys.years,
    queryFn: listAcademicYears,
  });

  const years = yearsQuery.data ?? [];
  const currentYear = useMemo<AcademicYear | undefined>(
    () => years.find((y) => y.current) ?? years[0],
    [years],
  );
  const activeYearId = selectedYearId ?? currentYear?.id;
  const activeYear = useMemo(
    () => years.find((y) => y.id === activeYearId) ?? currentYear,
    [years, activeYearId, currentYear],
  );

  const examsQuery = useQuery({
    queryKey: ['exams', activeYearId ?? 'current'],
    queryFn: () => listExams(activeYearId),
  });

  const saveMutation = useMutation({
    mutationFn: (request: ExamRequest) =>
      editing ? updateExam(editing.id, request) : createExam(request),
    onSuccess: () => {
      feedback.message.success(editing ? 'Exam updated' : 'Exam created');
      setModalOpen(false);
      setEditing(null);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['exams'] });
    },
    onError: (err) => {
      const fields = fieldErrors(err);
      const nameError = fields.find((f) => f.name === 'name');
      if (nameError) {
        form.setFields([{ name: 'name', errors: nameError.errors }]);
      } else {
        feedback.message.error(errorMessage(err));
      }
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteExam(id),
    onSuccess: () => {
      feedback.message.success('Exam deleted');
      queryClient.invalidateQueries({ queryKey: ['exams'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const publishMutation = useMutation({
    mutationFn: (id: number) => publishExam(id),
    onSuccess: () => {
      feedback.message.success('Results published');
      queryClient.invalidateQueries({ queryKey: ['exams'] });
      queryClient.invalidateQueries({ queryKey: ['mark-sheet'] });
      queryClient.invalidateQueries({ queryKey: ['class-result'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const unpublishMutation = useMutation({
    mutationFn: (id: number) => unpublishExam(id),
    onSuccess: () => {
      feedback.message.success('Results unpublished');
      queryClient.invalidateQueries({ queryKey: ['exams'] });
      queryClient.invalidateQueries({ queryKey: ['mark-sheet'] });
      queryClient.invalidateQueries({ queryKey: ['class-result'] });
    },
    onError: (err) => {
      feedback.message.error(errorMessage(err));
    },
  });

  const openCreateModal = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEditModal = (exam: ExamResponse) => {
    setEditing(exam);
    form.setFieldsValue({
      name: exam.name,
      dates: [dayjs(exam.startDate), dayjs(exam.endDate)],
    });
    setModalOpen(true);
  };

  const closeModal = () => {
    if (saveMutation.isPending) return;
    setModalOpen(false);
    setEditing(null);
    form.resetFields();
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const targetYearId = editing ? editing.academicYearId : activeYear?.id;
    if (!targetYearId) {
      feedback.message.error('Please create an academic year first.');
      return;
    }
    const startDate = toIsoDate(values.dates[0]);
    const endDate = toIsoDate(values.dates[1]);
    if (!startDate || !endDate) return;

    saveMutation.mutate({
      academicYearId: targetYearId,
      name: values.name.trim(),
      startDate,
      endDate,
    });
  };

  const columns: ColumnsType<ExamResponse> = [
    {
      title: 'Exam',
      dataIndex: 'name',
      key: 'name',
      render: (value: string) => <Text strong>{value}</Text>,
    },
    {
      title: 'Dates',
      key: 'dates',
      render: (_, record) => (
        <Text style={{ whiteSpace: 'nowrap' }}>
          {formatDate(record.startDate)} – {formatDate(record.endDate)}
        </Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status: string) => <StatusTag value={status} />,
    },
    {
      title: 'Published',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 170,
      render: (value?: string) => (
        <Text type="secondary" style={{ whiteSpace: 'nowrap' }}>
          {value ? formatDateTime(value) : '-'}
        </Text>
      ),
    },
  ];

  if (isAdmin) {
    columns.push({
      title: 'Actions',
      key: 'actions',
      width: 280,
      align: 'right',
      render: (_, record) => {
        const isPublished = record.status === 'PUBLISHED';
        return (
          <Space wrap size={8}>
            {isPublished ? (
              <Popconfirm
                title="Unpublish results?"
                description="Students will no longer see them."
                okText="Unpublish"
                onConfirm={() => unpublishMutation.mutate(record.id)}
              >
                <Button
                  size="small"
                  icon={<RollbackOutlined />}
                  loading={unpublishMutation.isPending && unpublishMutation.variables === record.id}
                >
                  Unpublish
                </Button>
              </Popconfirm>
            ) : (
              <Popconfirm
                title="Publish results?"
                description="Marks get locked and students are notified."
                okText="Publish"
                onConfirm={() => publishMutation.mutate(record.id)}
              >
                <Button
                  size="small"
                  type="primary"
                  icon={<CheckCircleOutlined />}
                  loading={publishMutation.isPending && publishMutation.variables === record.id}
                >
                  Publish
                </Button>
              </Popconfirm>
            )}
            <Button
              size="small"
              icon={<EditOutlined />}
              disabled={isPublished}
              onClick={() => openEditModal(record)}
            >
              Edit
            </Button>
            <Popconfirm
              title={`Delete ${record.name}?`}
              okText="Delete"
              okButtonProps={{ danger: true }}
              disabled={isPublished}
              onConfirm={() => deleteMutation.mutate(record.id)}
            >
              <Button
                size="small"
                danger
                icon={<DeleteOutlined />}
                disabled={isPublished}
                loading={deleteMutation.isPending && deleteMutation.variables === record.id}
              >
                Delete
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    });
  }

  const examsTab = (
    <div>
      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%', justifyContent: 'space-between' }}>
          <Select<number>
            value={activeYear?.id}
            loading={yearsQuery.isLoading}
            placeholder="Academic year"
            style={{ width: 220, maxWidth: '100%' }}
            options={years.map((y) => ({
              value: y.id,
              label: y.current ? `${y.name} (Current)` : y.name,
            }))}
            onChange={(value) => setSelectedYearId(value)}
          />

          {isAdmin && (
            <Button
              type="primary"
              icon={<PlusOutlined />}
              disabled={!activeYear}
              onClick={openCreateModal}
            >
              New exam
            </Button>
          )}
        </Space>
      </Card>

      {examsQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(examsQuery.error)}
          action={
            <Button size="small" onClick={() => examsQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<ExamResponse>
            rowKey="id"
            columns={columns}
            dataSource={examsQuery.data ?? []}
            loading={examsQuery.isLoading || examsQuery.isFetching}
            scroll={{ x: 680 }}
            pagination={false}
            locale={{
              emptyText: <Empty description="No exams yet" />,
            }}
          />
        </Card>
      )}
    </div>
  );

  return (
    <div>
      <PageHeader
        title="Exams & Marks"
        subtitle="Create exams, enter marks and publish results"
      />

      <Tabs
        defaultActiveKey="exams"
        items={[
          { key: 'exams', label: 'Exams', children: examsTab },
          { key: 'marks', label: 'Mark entry', children: <MarkEntryTab /> },
        ]}
      />

      <Modal
        title={
          editing
            ? `Edit ${editing.name}`
            : activeYear
              ? `New exam (${activeYear.name})`
              : 'New exam'
        }
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={closeModal}
        okText={editing ? 'Save' : 'Create'}
        confirmLoading={saveMutation.isPending}
        destroyOnClose
        style={{ maxWidth: 'calc(100vw - 32px)' }}
      >
        <Form<ExamFormValues> form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Name"
            rules={[
              { required: true, whitespace: true, message: 'Name is required' },
              { max: 100, message: 'Name must be at most 100 characters' },
            ]}
          >
            <Input maxLength={100} placeholder="Annual Examination" />
          </Form.Item>

          <Form.Item
            name="dates"
            label="Exam dates"
            rules={[{ required: true, message: 'Exam dates are required' }]}
          >
            <RangePicker style={{ width: '100%' }} format="YYYY-MM-DD" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
