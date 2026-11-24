import { Empty } from 'antd';
import type { SchoolClass } from '../../api/classes';

/** Students of one class. Placeholder: F13 (Students) fills this file. */
export default function ClassStudentsTab({ schoolClass }: { schoolClass: SchoolClass }) {
  return <Empty description={`Students of ${schoolClass.displayName} will appear here.`} />;
}
