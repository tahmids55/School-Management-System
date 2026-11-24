import { Tabs } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../components/common';
import ClassesTab from './ClassesTab';
import SubjectsTab from './SubjectsTab';
import AcademicYearsTab from './AcademicYearsTab';

const TAB_KEYS = ['classes', 'subjects', 'years'] as const;
type TabKey = (typeof TAB_KEYS)[number];

/** /academics (ADMIN): one page, three tabs. The open tab is kept in the URL (?tab=subjects). */
export default function AcademicsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const requested = searchParams.get('tab');
  const activeKey: TabKey = TAB_KEYS.includes(requested as TabKey) ? (requested as TabKey) : 'classes';

  return (
    <>
      <PageHeader title="Academics" subtitle="Academic years, classes, subjects and teacher assignments" />
      <Tabs
        activeKey={activeKey}
        onChange={(key) => setSearchParams({ tab: key }, { replace: true })}
        destroyOnHidden
        items={[
          { key: 'classes', label: 'Classes', children: <ClassesTab /> },
          { key: 'subjects', label: 'Subjects', children: <SubjectsTab /> },
          { key: 'years', label: 'Academic years', children: <AcademicYearsTab /> },
        ]}
      />
    </>
  );
}
