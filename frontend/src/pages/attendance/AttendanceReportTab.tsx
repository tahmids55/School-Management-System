import { Card, Empty } from 'antd';

/** Tab "Report" of /attendance. Slot file: F20 (Attendance reports) fills it. */
export default function AttendanceReportTab() {
  return (
    <Card>
      <Empty description="Choose a class to see its report" />
    </Card>
  );
}
