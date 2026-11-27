import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { PlusOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage, fieldErrors } from '../../api/client';
import {
  bulkCreateInvoices,
  cancelInvoice,
  createInvoice,
  feeKeys,
  getFeeSummary,
  INVOICE_STATUSES,
  listInvoices,
  type BulkInvoiceRequest,
  type Invoice,
  type InvoiceRequest,
  type InvoiceStatus,
} from '../../api/fees';
import { listStudents } from '../../api/students';
import { PageHeader, StatCard, StatusTag } from '../../components/common';
import { useClasses } from '../../hooks/useClasses';
import { feedback } from '../../utils/feedback';
import { formatDate, formatMoney, humanize, toIsoDate } from '../../utils/format';

const { Text } = Typography;

const PAGE_SIZE = 20;

interface CreateFormValues {
  studentId: number;
  title: string;
  amount: number;
  discount?: number;
  dueDate: unknown;
}

interface BulkFormValues {
  classId: number;
  title: string;
  amount: number;
  dueDate: unknown;
}

/** /fees (ADMIN): summary cards, invoice search/filters, create (remote student search), bulk invoice, cancel. */
export default function FeesPage() {
  const queryClient = useQueryClient();
  const [searchText, setSearchText] = useState('');
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<InvoiceStatus>();
  const [classId, setClassId] = useState<number>();
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [page, setPage] = useState(0);

  const [createOpen, setCreateOpen] = useState(false);
  const [bulkOpen, setBulkOpen] = useState(false);
  const [studentQuery, setStudentQuery] = useState('');

  const [createForm] = Form.useForm<CreateFormValues>();
  const [bulkForm] = Form.useForm<BulkFormValues>();

  const classes = useClasses();
  const summary = useQuery({
    queryKey: feeKeys.summary,
    queryFn: getFeeSummary,
  });

  const search = {
    q: q || undefined,
    status,
    classId,
    overdue: overdueOnly || undefined,
    page,
    size: PAGE_SIZE,
  };
  const invoices = useQuery({
    queryKey: feeKeys.list(search),
    queryFn: () => listInvoices(search),
    placeholderData: keepPreviousData,
  });

  const studentOptionsQuery = useQuery({
    queryKey: ['fees', 'student-picker', studentQuery],
    queryFn: () => listStudents({ q: studentQuery || undefined, status: 'ACTIVE', size: 20 }),
    enabled: createOpen,
  });

  const createMutation = useMutation({
    mutationFn: (request: InvoiceRequest) => createInvoice(request),
    onSuccess: () => {
      feedback.message.success('Invoice created');
      setCreateOpen(false);
      createForm.resetFields();
      setStudentQuery('');
      void queryClient.invalidateQueries({ queryKey: feeKeys.all });
    },
    onError: (error) => {
      const fields = fieldErrors(error);
      if (fields.length > 0) createForm.setFields(fields);
      feedback.message.error(errorMessage(error));
    },
  });

  const bulkMutation = useMutation({
    mutationFn: (request: BulkInvoiceRequest) => bulkCreateInvoices(request),
    onSuccess: (result) => {
      feedback.message.success(`Created ${result.created} invoice(s), skipped ${result.skipped}`);
      setBulkOpen(false);
      bulkForm.resetFields();
      void queryClient.invalidateQueries({ queryKey: feeKeys.all });
    },
    onError: (error) => {
      const fields = fieldErrors(error);
      if (fields.length > 0) bulkForm.setFields(fields);
      feedback.message.error(errorMessage(error));
    },
  });

  const cancelMutation = useMutation({
    mutationFn: (id: number) => cancelInvoice(id),
    onSuccess: () => {
      feedback.message.success('Invoice cancelled');
      void queryClient.invalidateQueries({ queryKey: feeKeys.all });
    },
    onError: (error) => feedback.message.error(errorMessage(error)),
  });

  function applySearch(value: string) {
    setQ(value.trim());
    setPage(0);
  }

  function confirmCancel(invoice: Invoice) {
    feedback.modal.confirm({
      title: `Cancel invoice ${invoice.invoiceNo}?`,
      content: `${invoice.title} for ${invoice.studentName} (${formatMoney(invoice.payable)})`,
      okText: 'Cancel invoice',
      okButtonProps: { danger: true },
      onOk: () => cancelMutation.mutateAsync(invoice.id).catch(() => undefined),
    });
  }

  const columns: ColumnsType<Invoice> = [
    {
      title: 'Invoice',
      dataIndex: 'invoiceNo',
      render: (value: string, record: Invoice) => (
        <Space size={6} wrap>
          <Text strong>{value}</Text>
          {record.overdue && <StatusTag value="OVERDUE" />}
        </Space>
      ),
    },
    {
      title: 'Student',
      dataIndex: 'studentName',
      render: (name: string, record: Invoice) => (
        <div>
          <Link to={`/students/${record.studentId}`}>{name}</Link>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.admissionNo}
              {record.className ? ` · ${record.className}` : ''}
            </Text>
          </div>
        </div>
      ),
    },
    { title: 'Title', dataIndex: 'title' },
    { title: 'Due', dataIndex: 'dueDate', render: (value: string) => formatDate(value) },
    {
      title: 'Payable',
      dataIndex: 'payable',
      align: 'right',
      render: (value: number, record: Invoice) => (
        <div>
          <div>{formatMoney(value)}</div>
          {record.discount > 0 && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Discount {formatMoney(record.discount)}
            </Text>
          )}
        </div>
      ),
    },
    {
      title: 'Outstanding',
      dataIndex: 'outstanding',
      align: 'right',
      render: (value: number) => formatMoney(value),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (value: InvoiceStatus) => <StatusTag value={value} />,
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'right',
      render: (_: unknown, record: Invoice) =>
        record.amountPaid === 0 && record.status !== 'CANCELLED' ? (
          <Button
            size="small"
            danger
            loading={cancelMutation.isPending && cancelMutation.variables === record.id}
            onClick={() => confirmCancel(record)}
          >
            Cancel invoice
          </Button>
        ) : null,
    },
  ];

  const classOptions = (classes.data ?? []).map((c) => ({ value: c.id, label: c.displayName }));
  const statusOptions = INVOICE_STATUSES.map((value) => ({ value, label: humanize(value) }));
  const studentOptions = (studentOptionsQuery.data?.content ?? []).map((s) => ({
    value: s.id,
    label: `${s.fullName} (${s.admissionNo})${s.className ? ` · ${s.className}` : ''}`,
  }));
  const filtered = Boolean(q || status || classId || overdueOnly);

  return (
    <div>
      <PageHeader
        title="Fees"
        subtitle="Invoices, payments and dues"
        actions={
          <>
            <Button onClick={() => setBulkOpen(true)}>Invoice a class</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
              New invoice
            </Button>
          </>
        }
      />

      {summary.isError ? (
        <Alert
          type="error"
          showIcon
          style={{ marginBottom: 16 }}
          message={errorMessage(summary.error)}
          action={<Button onClick={() => void summary.refetch()}>Try again</Button>}
        />
      ) : (
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              label="Billed"
              value={summary.isLoading ? '-' : formatMoney(summary.data?.totalBilled)}
              tint="sky"
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              label="Collected"
              value={summary.isLoading ? '-' : formatMoney(summary.data?.totalCollected)}
              tint="mint"
              hint={summary.data ? `${summary.data.collectionRate.toFixed(1)}% collection rate` : undefined}
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              label="Outstanding"
              value={summary.isLoading ? '-' : formatMoney(summary.data?.totalOutstanding)}
              tint="peach"
            />
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <StatCard
              label="Overdue invoices"
              value={summary.isLoading ? '-' : (summary.data?.overdueInvoices ?? 0)}
              tint="rose"
            />
          </Col>
        </Row>
      )}

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Input.Search
            placeholder="Invoice, title, student or admission no."
            aria-label="Invoice, title, student or admission no."
            allowClear
            value={searchText}
            style={{ width: 320, maxWidth: '100%' }}
            onChange={(event) => {
              setSearchText(event.target.value);
              if (event.target.value === '') applySearch('');
            }}
            onSearch={applySearch}
          />
          <Select
            placeholder="Any status"
            aria-label="Any status"
            allowClear
            options={statusOptions}
            value={status}
            style={{ width: 170, maxWidth: '100%' }}
            onChange={(value?: InvoiceStatus) => {
              setStatus(value);
              setPage(0);
            }}
          />
          <Select
            placeholder="All classes"
            aria-label="All classes"
            allowClear
            loading={classes.isLoading}
            options={classOptions}
            value={classId}
            style={{ width: 180, maxWidth: '100%' }}
            onChange={(value?: number) => {
              setClassId(value);
              setPage(0);
            }}
          />
          <Space size={8}>
            <Switch
              aria-label="Overdue only"
              checked={overdueOnly}
              onChange={(checked) => {
                setOverdueOnly(checked);
                setPage(0);
              }}
            />
            <Text>Overdue only</Text>
          </Space>
        </Space>
      </Card>

      {invoices.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(invoices.error)}
          action={<Button onClick={() => void invoices.refetch()}>Try again</Button>}
        />
      ) : (
        <Card styles={{ body: { padding: 0 } }}>
          <Table<Invoice>
            rowKey="id"
            columns={columns}
            dataSource={invoices.data?.content ?? []}
            loading={invoices.isFetching}
            scroll={{ x: 'max-content' }}
            locale={{
              emptyText: (
                <Empty
                  description={filtered ? 'No invoices match these filters' : 'No fee invoices yet'}
                />
              ),
            }}
            pagination={{
              current: page + 1,
              pageSize: PAGE_SIZE,
              total: invoices.data?.totalElements ?? 0,
              showSizeChanger: false,
              showTotal: (total) => `${total} invoices`,
              onChange: (next) => setPage(next - 1),
            }}
          />
        </Card>
      )}

      <Modal
        title="New invoice"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false);
          createForm.resetFields();
        }}
        onOk={() => createForm.submit()}
        okText="Create"
        confirmLoading={createMutation.isPending}
        destroyOnClose
      >
        <Form<CreateFormValues>
          form={createForm}
          layout="vertical"
          initialValues={{ discount: 0 }}
          onFinish={(values) => {
            const dueDate = toIsoDate(values.dueDate as Parameters<typeof toIsoDate>[0]);
            if (!dueDate) return;
            createMutation.mutate({
              studentId: values.studentId,
              title: values.title.trim(),
              amount: values.amount,
              discount: values.discount ?? 0,
              dueDate,
            });
          }}
        >
          <Form.Item
            name="studentId"
            label="Student"
            rules={[{ required: true, message: 'Select a student' }]}
          >
            <Select
              showSearch
              filterOption={false}
              placeholder="Search by name or admission no."
              options={studentOptions}
              onSearch={setStudentQuery}
              notFoundContent={studentOptionsQuery.isFetching ? <Spin size="small" /> : 'No active students found'}
            />
          </Form.Item>
          <Form.Item
            name="title"
            label="Title"
            rules={[
              { required: true, message: 'Enter a title' },
              { max: 120, message: 'Title must be at most 120 characters' },
            ]}
          >
            <Input placeholder="Tuition Fee - December 2026" maxLength={120} />
          </Form.Item>
          <Row gutter={12}>
            <Col xs={24} sm={12}>
              <Form.Item
                name="amount"
                label="Amount (BDT)"
                rules={[{ required: true, message: 'Enter an amount' }]}
              >
                <InputNumber style={{ width: '100%' }} min={1} max={1000000} precision={2} />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="discount" label="Discount">
                <InputNumber style={{ width: '100%' }} min={0} max={1000000} precision={2} />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item
            name="dueDate"
            label="Due date"
            rules={[{ required: true, message: 'Select a due date' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Invoice a class"
        open={bulkOpen}
        onCancel={() => {
          setBulkOpen(false);
          bulkForm.resetFields();
        }}
        onOk={() => bulkForm.submit()}
        okText="Create"
        confirmLoading={bulkMutation.isPending}
        destroyOnClose
      >
        <Form<BulkFormValues>
          form={bulkForm}
          layout="vertical"
          onFinish={(values) => {
            const dueDate = toIsoDate(values.dueDate as Parameters<typeof toIsoDate>[0]);
            if (!dueDate) return;
            bulkMutation.mutate({
              classId: values.classId,
              title: values.title.trim(),
              amount: values.amount,
              dueDate,
            });
          }}
        >
          <Form.Item
            name="classId"
            label="Class"
            rules={[{ required: true, message: 'Select a class' }]}
          >
            <Select placeholder="All classes" options={classOptions} loading={classes.isLoading} />
          </Form.Item>
          <Form.Item
            name="title"
            label="Title"
            rules={[
              { required: true, message: 'Enter a title' },
              { max: 120, message: 'Title must be at most 120 characters' },
            ]}
          >
            <Input placeholder="Tuition Fee - December 2026" maxLength={120} />
          </Form.Item>
          <Form.Item
            name="amount"
            label="Amount (BDT)"
            rules={[{ required: true, message: 'Enter an amount' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} max={1000000} precision={2} />
          </Form.Item>
          <Form.Item
            name="dueDate"
            label="Due date"
            rules={[{ required: true, message: 'Select a due date' }]}
          >
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
