import { Empty, Table } from 'antd';

/** Fee invoices of one student. Slot file: F21 (Fee invoices) fills it and uses studentId. */
export default function StudentFeesTab(_props: { studentId: number }) {
  return (
    <Table
      rowKey="id"
      size="small"
      dataSource={[]}
      pagination={false}
      scroll={{ x: 'max-content' }}
      columns={[
        { title: 'Invoice', dataIndex: 'invoiceNo' },
        { title: 'Title', dataIndex: 'title' },
        { title: 'Due', dataIndex: 'dueDate' },
        { title: 'Payable', dataIndex: 'payable', align: 'right' },
        { title: 'Outstanding', dataIndex: 'outstanding', align: 'right' },
      ]}
      locale={{ emptyText: <Empty description="No fee invoices yet" /> }}
    />
  );
}
