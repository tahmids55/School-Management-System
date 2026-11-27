import { Alert, Button, Empty, Space, Spin, Table, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { errorMessage } from '../../api/client';
import { feeKeys, listStudentInvoices, type Invoice, type InvoiceStatus } from '../../api/fees';
import { StatusTag } from '../../components/common';
import { formatDate, formatMoney } from '../../utils/format';

const { Text } = Typography;

/** Fee invoices of one student, newest due date first (F21). */
export default function StudentFeesTab({ studentId }: { studentId: number }) {
  const query = useQuery({
    queryKey: feeKeys.student(studentId),
    queryFn: () => listStudentInvoices(studentId),
  });

  if (query.isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
        <Spin />
      </div>
    );
  }
  if (query.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={errorMessage(query.error)}
        action={
          <Button size="small" onClick={() => void query.refetch()}>
            Retry
          </Button>
        }
      />
    );
  }

  const invoices = query.data ?? [];

  return (
    <Table<Invoice>
      rowKey="id"
      size="small"
      dataSource={invoices}
      pagination={{ pageSize: 20, hideOnSinglePage: true }}
      scroll={{ x: 'max-content' }}
      columns={[
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
        { title: 'Due', dataIndex: 'dueDate', render: (value: string) => formatDate(value) },
        {
          title: 'Payable',
          dataIndex: 'payable',
          align: 'right',
          render: (value: number) => formatMoney(value),
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
      ]}
      locale={{ emptyText: <Empty description="No fee invoices yet" /> }}
    />
  );
}
