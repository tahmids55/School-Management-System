import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  Col,
  DatePicker,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Typography,
} from 'antd';
import type { MenuProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownOutlined, PlusOutlined } from '@ant-design/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage, fieldErrors, newIdempotencyKey } from '../../api/client';
import {
  bulkCreateInvoices,
  cancelInvoice,
  createInvoice,
  feeKeys,
  getFeeSummary,
  INVOICE_STATUSES,
  listInvoicePayments,
  listInvoices,
  PAYMENT_METHOD_LABELS,
  recordPayment,
  type BulkInvoiceRequest,
  type Invoice,
  type InvoiceRequest,
  type InvoiceStatus,
  type PaymentMethod,
  type PaymentRequest,
  type PaymentResponse,
} from '../../api/fees';
import { listStudents } from '../../api/students';
import { PageHeader, StatCard, StatusTag } from '../../components/common';
import { useClasses } from '../../hooks/useClasses';
import { feedback } from '../../utils/feedback';
import { formatDate, formatDateTime, formatMoney, humanize, toIsoDate } from '../../utils/format';

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

interface PayFormValues {
  method: PaymentMethod;
  amount: number;
  reference?: string;
}

/** /fees (ADMIN): summary cards, invoice search/filters, create, bulk invoice, record payment, payment history, cancel. */
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
  const [payingInvoice, setPayingInvoice] = useState<Invoice | null>(null);
  const [attemptKey, setAttemptKey] = useState<string>('');
  const [payError, setPayError] = useState<string | null>(null);
  const [historyInvoice, setHistoryInvoice] = useState<Invoice | null>(null);

  const [createForm] = Form.useForm<CreateFormValues>();
  const [bulkForm] = Form.useForm<BulkFormValues>();
  const [payForm] = Form.useForm<PayFormValues>();
  const watchedMethod = Form.useWatch('method', payForm);

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

  const historyQuery = useQuery({
    queryKey: feeKeys.payments(historyInvoice?.id ?? 0),
    queryFn: () => listInvoicePayments(historyInvoice!.id),
    enabled: historyInvoice !== null,
  });

  const payMutation = useMutation({
    mutationFn: ({ invoiceId, request, key }: { invoiceId: number; request: PaymentRequest; key: string }) =>
      recordPayment(invoiceId, request, key),
    onSuccess: (recorded) => {
      feedback.message.success(`Payment recorded (${recorded.transactionRef})`);
      closePayModal();
      void queryClient.invalidateQueries({ queryKey: feeKeys.all });
    },
    onError: (error) => {
      // Keep attemptKey unchanged so a retry after network failure or decline reuses the same Idempotency-Key.
      const fields = fieldErrors(error).filter((f): f is { name: keyof PayFormValues; errors: string[] } =>
        f.name === 'method' || f.name === 'amount' || f.name === 'reference',
      );
      if (fields.length > 0) payForm.setFields(fields);
      const message = errorMessage(error);
      setPayError(message);
      feedback.message.error(message);
    },
  });

  function openPayModal(invoice: Invoice) {
    setPayingInvoice(invoice);
    setAttemptKey(newIdempotencyKey());
    setPayError(null);
    payForm.setFieldsValue({
      method: 'CASH',
      amount: Number(invoice.outstanding),
      reference: undefined,
    });
  }

  function closePayModal() {
    setPayingInvoice(null);
    setAttemptKey('');
    setPayError(null);
    payForm.resetFields();
  }

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
      render: (_: unknown, record: Invoice) => {
        const canPay = record.status === 'UNPAID' || record.status === 'PARTIALLY_PAID';
        const canCancel = record.amountPaid === 0 && record.status !== 'CANCELLED';
        const menuItems: MenuProps['items'] = [
          {
            key: 'history',
            label: 'Payment history',
            onClick: () => setHistoryInvoice(record),
          },
          ...(canCancel
            ? [
                {
                  key: 'cancel',
                  label: 'Cancel invoice',
                  danger: true,
                  onClick: () => confirmCancel(record),
                },
              ]
            : []),
        ];
        return (
          <Space size={6}>
            {canPay && (
              <Button type="primary" size="small" onClick={() => openPayModal(record)}>
                Pay
              </Button>
            )}
            <Dropdown menu={{ items: menuItems }} trigger={['click']}>
              <Button
                size="small"
                loading={cancelMutation.isPending && cancelMutation.variables === record.id}
              >
                More <DownOutlined />
              </Button>
            </Dropdown>
          </Space>
        );
      },
    },
  ];

  const paymentHistoryColumns: ColumnsType<PaymentResponse> = [
    { title: 'When', dataIndex: 'paidAt', render: (value: string) => formatDateTime(value) },
    {
      title: 'Amount',
      dataIndex: 'amount',
      align: 'right',
      render: (value: number) => formatMoney(value),
    },
    {
      title: 'Method',
      dataIndex: 'method',
      render: (value: PaymentMethod) => PAYMENT_METHOD_LABELS[value] ?? humanize(value),
    },
    { title: 'Reference', dataIndex: 'transactionRef' },
    { title: 'By', dataIndex: 'receivedBy' },
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

      <Modal
        title={payingInvoice ? `Record payment - ${payingInvoice.invoiceNo}` : 'Record payment'}
        open={payingInvoice !== null}
        onCancel={closePayModal}
        onOk={() => payForm.submit()}
        okText="Record payment"
        confirmLoading={payMutation.isPending}
        destroyOnClose
      >
        {payingInvoice && (
          <div style={{ marginBottom: 12 }}>
            <Text type="secondary">
              {payingInvoice.studentName} ({payingInvoice.admissionNo}) · Outstanding{' '}
              {formatMoney(payingInvoice.outstanding)}
            </Text>
          </div>
        )}
        {payError && (
          <Alert type="error" showIcon message={payError} style={{ marginBottom: 12 }} />
        )}
        <Form<PayFormValues>
          form={payForm}
          layout="vertical"
          onFinish={(values) => {
            if (!payingInvoice) return;
            setPayError(null);
            payMutation.mutate({
              invoiceId: payingInvoice.id,
              request: {
                method: values.method,
                amount: values.amount,
                reference:
                  values.method === 'BANK_TRANSFER' && values.reference
                    ? values.reference.trim()
                    : undefined,
              },
              key: attemptKey,
            });
          }}
        >
          <Form.Item
            name="method"
            label="Method"
            rules={[{ required: true, message: 'Select a payment method' }]}
          >
            <Radio.Group optionType="button" buttonStyle="solid">
              <Radio.Button value="CASH">Cash</Radio.Button>
              <Radio.Button value="BKASH">bKash</Radio.Button>
              <Radio.Button value="BANK_TRANSFER">Bank transfer</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            name="amount"
            label="Amount (BDT)"
            rules={[{ required: true, message: 'Enter an amount' }]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0.01}
              max={payingInvoice ? Number(payingInvoice.outstanding) : undefined}
              precision={2}
            />
          </Form.Item>
          {watchedMethod === 'BANK_TRANSFER' && (
            <Form.Item
              name="reference"
              label="Bank transaction reference"
              rules={[
                { required: true, whitespace: true, message: 'Enter the bank transaction reference' },
                { max: 80, message: 'Reference must be at most 80 characters' },
              ]}
            >
              <Input placeholder="Bank transaction reference" maxLength={80} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title={historyInvoice ? `Payments for ${historyInvoice.invoiceNo}` : 'Payment history'}
        open={historyInvoice !== null}
        onCancel={() => setHistoryInvoice(null)}
        footer={null}
        width={680}
        destroyOnClose
      >
        {historyQuery.isError ? (
          <Alert
            type="error"
            showIcon
            message={errorMessage(historyQuery.error)}
            action={<Button onClick={() => void historyQuery.refetch()}>Try again</Button>}
          />
        ) : (
          <Table<PaymentResponse>
            rowKey="id"
            size="small"
            columns={paymentHistoryColumns}
            dataSource={historyQuery.data ?? []}
            loading={historyQuery.isLoading || historyQuery.isFetching}
            pagination={false}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No payments recorded yet" /> }}
          />
        )}
      </Modal>
    </div>
  );
}
