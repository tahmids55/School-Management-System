import { Card, Empty } from 'antd';

/**
 * Separate tab component reserved for F24 (Marks entry).
 */
export default function MarkEntryTab() {
  return (
    <Card>
      <Empty description="Choose an exam, class and subject to enter marks" />
    </Card>
  );
}
