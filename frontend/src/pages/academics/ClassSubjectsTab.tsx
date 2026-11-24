import { Empty } from 'antd';
import type { SchoolClass } from '../../api/classes';

/** Subjects and their teachers for one class. Placeholder: F16 (Teacher assignment) fills this file. */
export default function ClassSubjectsTab({ schoolClass }: { schoolClass: SchoolClass }) {
  return <Empty description={`Subjects of ${schoolClass.displayName} will appear here.`} />;
}
