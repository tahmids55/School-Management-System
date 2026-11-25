import { Empty, Table } from 'antd';

/** Attendance of one student. Slot file: F20 (Attendance reports) fills it and uses studentId. */
export default function StudentAttendanceTab(_props: { studentId: number }) {
  return (
    <Table
      rowKey="date"
      size="small"
      dataSource={[]}
      pagination={false}
      scroll={{ x: 'max-content' }}
      columns={[
        { title: 'Date', dataIndex: 'date' },
        { title: 'Status', dataIndex: 'status' },
        { title: 'Remarks', dataIndex: 'remarks' },
      ]}
      locale={{ emptyText: <Empty description="No attendance recorded yet" /> }}
    />
  );
}
