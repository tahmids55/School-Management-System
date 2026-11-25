import { Descriptions, Drawer, Tabs } from 'antd';
import type { SchoolClass } from '../../api/classes';
import ClassStudentsTab from './ClassStudentsTab';
import ClassSubjectsTab from './ClassSubjectsTab';

interface Props {
  /** The class to show; undefined = closed. */
  schoolClass?: SchoolClass;
  onClose: () => void;
}

/** Opens from a row of the Classes tab. F13 fills the Students tab, F16 the Subjects & teachers tab. */
export default function ClassDetailDrawer({ schoolClass, onClose }: Props) {
  return (
    <Drawer
      open={schoolClass !== undefined}
      onClose={onClose}
      title={schoolClass?.displayName}
      width="min(720px, 100vw)"
      destroyOnHidden
    >
      {schoolClass && (
        <>
          <Descriptions size="small" column={{ xs: 1, sm: 2 }} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="Academic year">{schoolClass.academicYearName}</Descriptions.Item>
            <Descriptions.Item label="Room">{schoolClass.room || '-'}</Descriptions.Item>
            <Descriptions.Item label="Class teacher">{schoolClass.classTeacherName || 'Not set'}</Descriptions.Item>
            <Descriptions.Item label="Students">{schoolClass.studentCount}</Descriptions.Item>
            <Descriptions.Item label="Capacity">{schoolClass.capacity}</Descriptions.Item>
          </Descriptions>
          <Tabs
            items={[
              { key: 'students', label: 'Students', children: <ClassStudentsTab schoolClass={schoolClass} /> },
              {
                key: 'subjects',
                label: 'Subjects & teachers',
                children: <ClassSubjectsTab schoolClass={schoolClass} />,
              },
            ]}
          />
        </>
      )}
    </Drawer>
  );
}
