import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  InputNumber,
  Modal,
  Row,
  Space,
  Spin,
  Table,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CheckCircleFilled } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { errorMessage, fieldErrors, newIdempotencyKey } from '../../api/client';
import {
  feeKeys,
  listInvoicePayments,
  listStudentInvoices,
  payOwnInvoice,
  PAYMENT_METHOD_LABELS,
  type Invoice,
  type InvoiceStatus,
  type OnlinePaymentRequest,
  type PaymentMethod,
  type PaymentResponse,
} from '../../api/fees';
import { useAuth } from '../../auth/AuthContext';
import { PageHeader, StatCard, StatusTag } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { formatDate, formatDateTime, formatMoney, humanize } from '../../utils/format';

const { Text } = Typography;

interface PayFormValues {
  amount: number;
}

/** /my/fees (STUDENT): Total due and Paid so far, own invoices, bKash sandbox payment, and receipt reference. */
export default function MyFeesPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const studentId = user?.studentId ?? null;

  const [payingInvoice, setPayingInvoice] = useState<Invoice | null>(null);
  const [attemptKey, setAttemptKey] = useState('');
  const [payError, setPayError] = useState<string | null>(null);
  const [successPayment, setSuccessPayment] = useState<PaymentResponse | null>(null);
  const [historyInvoice, setHistoryInvoice] = useState<Invoice | null>(null);

  const [payForm] = Form.useForm<PayFormValues>();

  const invoicesQuery = useQuery({
    queryKey: feeKeys.student(studentId ?? 0),
    queryFn: () => listStudentInvoices(studentId!),
    enabled: studentId !== null,
  });

  const historyQuery = useQuery({
    queryKey: feeKeys.payments(historyInvoice?.id ?? 0),
    queryFn: () => listInvoicePayments(historyInvoice!.id),
    enabled: historyInvoice !== null,
  });

  const payMutation = useMutation({
    mutationFn: ({ invoiceId, request, key }: { invoiceId: number; request: OnlinePaymentRequest; key: string }) =>
      payOwnInvoice(invoiceId, request, key),
    onSuccess: (recorded) => {
      closePayModal();
      setSuccessPayment(recorded);
      void queryClient.invalidateQueries({ queryKey: feeKeys.all });
      void queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
    onError: (error) => {
      // Keep attemptKey unchanged so retrying after a failure reuses the same Idempotency-Key.
      const fields = fieldErrors(error).filter((f): f is { name: keyof PayFormValues; errors: string[] } =>
        f.name === 'amount',
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
    payForm.setFieldsValue({ amount: Number(invoice.outstanding) });
  }

  function closePayModal() {
    setPayingInvoice(null);
    setAttemptKey('');
    setPayError(null);
    payForm.resetFields();
  }

  const invoices = invoicesQuery.data ?? [];
  const activeInvoices = invoices.filter((inv) => inv.status !== 'CANCELLED');
  const totalDue = activeInvoices.reduce((sum, inv) => sum + Number(inv.outstanding), 0);
  const paidSoFar = activeInvoices.reduce((sum, inv) => sum + Number(inv.amountPaid), 0);

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
    { title: 'Title', dataIndex: 'title' },
    { title: 'Due date', dataIndex: 'dueDate', render: (value: string) => formatDate(value) },
    {
      title: 'Amount',
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
        const canPay = (record.status === 'UNPAID' || record.status === 'PARTIALLY_PAID') && record.outstanding > 0;
        return (
          <Space size={6}>
            {canPay && (
              <Button type="primary" size="small" onClick={() => openPayModal(record)}>
                Pay now
              </Button>
            )}
            <Button size="small" onClick={() => setHistoryInvoice(record)}>
              Payments
            </Button>
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
  ];

  let body;
  if (studentId === null) {
    body = <Alert type="warning" showIcon message="No student profile is linked to this account" />;
  } else if (invoicesQuery.isLoading) {
    body = (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  } else if (invoicesQuery.isError) {
    body = (
      <Alert
        type="error"
        showIcon
        message={errorMessage(invoicesQuery.error)}
        action={
          <Button size="small" onClick={() => void invoicesQuery.refetch()}>
            Retry
          </Button>
        }
      />
    );
  } else {
    body = (
      <>
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} sm={12}>
            <StatCard label="Total due" value={formatMoney(totalDue)} tint={totalDue > 0 ? 'peach' : 'mint'} />
          </Col>
          <Col xs={24} sm={12}>
            <StatCard label="Paid so far" value={formatMoney(paidSoFar)} tint="mint" />
          </Col>
        </Row>

        <Card styles={{ body: { padding: 0 } }}>
          <Table<Invoice>
            rowKey="id"
            columns={columns}
            dataSource={invoices}
            loading={invoicesQuery.isFetching}
            pagination={{ pageSize: 20, hideOnSinglePage: true }}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: <Empty description="No fee invoices yet" /> }}
          />
        </Card>
      </>
    );
  }

  return (
    <div>
      <PageHeader title="My fees" subtitle="Invoices and online payment" />
      {body}

      <Modal
        title="Pay with bKash (sandbox)"
        open={payingInvoice !== null}
        onCancel={closePayModal}
        onOk={() => payForm.submit()}
        okText="Pay with bKash"
        confirmLoading={payMutation.isPending}
        destroyOnClose
      >
        {payingInvoice && (
          <div style={{ marginBottom: 12 }}>
            <Text type="secondary">
              {payingInvoice.invoiceNo} · {payingInvoice.title} · Outstanding {formatMoney(payingInvoice.outstanding)}
            </Text>
          </div>
        )}
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="Sandbox: payments up to BDT 50,000 are approved; no real money moves."
        />
        {payError && <Alert type="error" showIcon message={payError} style={{ marginBottom: 12 }} />}
        <Form<PayFormValues>
          form={payForm}
          layout="vertical"
          onFinish={(values) => {
            if (!payingInvoice || payMutation.isPending) return;
            setPayError(null);
            payMutation.mutate({
              invoiceId: payingInvoice.id,
              request: { amount: values.amount },
              key: attemptKey,
            });
          }}
        >
          <Form.Item
            name="amount"
            label="Amount (BDT)"
            rules={[
              { required: true, message: 'Enter an amount' },
              { type: 'number', min: 0.01, message: 'Amount must be at least 0.01' },
              ...(payingInvoice
                ? [
                    {
                      type: 'number' as const,
                      max: Number(payingInvoice.outstanding),
                      message: 'Amount cannot exceed outstanding',
                    },
                  ]
                : []),
            ]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={0.01}
              max={payingInvoice ? Number(payingInvoice.outstanding) : undefined}
              precision={2}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={
          <Space size={8}>
            <CheckCircleFilled style={{ color: '#16a34a' }} />
            <span>Payment successful</span>
          </Space>
        }
        open={successPayment !== null}
        onCancel={() => setSuccessPayment(null)}
        footer={
          <Button type="primary" onClick={() => setSuccessPayment(null)}>
            Done
          </Button>
        }
        destroyOnClose
      >
        {successPayment && (
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="Invoice">{successPayment.invoiceNo}</Descriptions.Item>
            <Descriptions.Item label="Amount">{formatMoney(successPayment.amount)}</Descriptions.Item>
            <Descriptions.Item label="bKash reference">
              <Text strong copyable>
                {successPayment.transactionRef}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="Paid at">{formatDateTime(successPayment.paidAt)}</Descriptions.Item>
            <Descriptions.Item label="Status">
              <StatusTag value={successPayment.invoiceStatus} />
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      <Modal
        title={historyInvoice ? `Payments for ${historyInvoice.invoiceNo}` : 'Payment history'}
        open={historyInvoice !== null}
        onCancel={() => setHistoryInvoice(null)}
        footer={null}
        width={640}
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
