import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, List, Row, Spin, Tag, Typography } from 'antd';
import { errorMessage } from '../../api/client';
import { dashboardKeys, getTeacherDashboard } from '../../api/dashboard';
import { PageHeader, StatCard } from '../../components/common';
import NoticeList from './NoticeList';

const { Text } = Typography;

export default function TeacherDashboardView() {
  const query = useQuery({ queryKey: dashboardKeys.teacher, queryFn: getTeacherDashboard });

  if (query.isError) {
    return (
      <Alert
        type="error"
        showIcon
        message={errorMessage(query.error)}
        action={<Button onClick={() => void query.refetch()}>Try again</Button>}
      />
    );
  }
  if (!query.data) {
    return (
      <div className="full-page-center">
        <Spin size="large" />
      </div>
    );
  }

  const data = query.data;
  const subjects = data.teachingLoad.subjects;
  const pending = data.classesToday.filter((c) => !c.attendanceTaken).length;

  return (
    <>
      <PageHeader title={`Welcome, ${data.teacherName}`} subtitle="Your classes and tasks for today" />

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <StatCard label="My classes" value={data.classesToday.length} tint="sky" />
        </Col>
        <Col xs={12} sm={8}>
          <StatCard label="Subjects I teach" value={subjects.length} tint="mint" />
        </Col>
        <Col xs={12} sm={8}>
          <StatCard label="Attendance pending today" value={pending} tint={pending > 0 ? 'peach' : 'lavender'} />
        </Col>
      </Row>

      <Card title="Today's attendance" style={{ marginBottom: 16 }}>
        {data.classesToday.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="You are not assigned to any class yet" />
        ) : (
          <Row gutter={[12, 12]}>
            {data.classesToday.map((c) => (
              <Col key={c.classId} xs={24} sm={12} lg={8}>
                {/* Opens the attendance sheet of this class for today. */}
                <Link to={`/attendance?classId=${c.classId}`}>
                  <Card size="small" hoverable>
                    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                      <div>
                        <Text strong>{c.className}</Text>
                        <br />
                        <Text type="secondary">{`${c.students} students`}</Text>
                      </div>
                      {c.attendanceTaken ? (
                        <Tag color="green" bordered={false}>Taken</Tag>
                      ) : (
                        <Tag color="orange" bordered={false}>Pending</Tag>
                      )}
                    </div>
                  </Card>
                </Link>
              </Col>
            ))}
          </Row>
        )}
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="Subjects I teach" style={{ height: '100%' }}>
            {subjects.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="You are not assigned to any class yet" />
            ) : (
              <List
                dataSource={subjects}
                rowKey={(s) => `${s.classId}-${s.subjectId}`}
                renderItem={(s) => (
                  <List.Item
                    style={{ paddingInline: 0 }}
                    actions={[
                      <Link key="marks" to="/exams">
                        <Button size="small">Enter marks</Button>
                      </Link>,
                    ]}
                  >
                    <List.Item.Meta
                      title={`${s.subjectName} (${s.subjectCode})`}
                      description={`${s.className} · ${s.academicYear}`}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <NoticeList notices={data.notices} />
        </Col>
      </Row>
    </>
  );
}
