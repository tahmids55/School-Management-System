import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Descriptions, Empty, Progress, Row, Space, Spin, Tag, Typography } from 'antd';
import { errorMessage } from '../../api/client';
import { dashboardKeys, getStudentDashboard } from '../../api/dashboard';
import { GradeTag, PageHeader, StatCard } from '../../components/common';
import { colors } from '../../theme';
import { formatDate, formatMoney, formatPercent } from '../../utils/format';
import { firstName } from './greeting';
import NoticeList from './NoticeList';

const { Text } = Typography;

/** The student's home (F31): one request, GET /dashboard/student, assembled by the server's facade. */
export default function StudentDashboardView() {
  const query = useQuery({ queryKey: dashboardKeys.student, queryFn: getStudentDashboard });

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
  const result = data.latestResult;
  const placement = data.className
    ? `${data.className}${data.rollNo ? ` · Roll ${data.rollNo}` : ''}`
    : 'Not assigned to a class yet';
  const owes = Number(data.outstandingFees) > 0;

  return (
    <>
      <PageHeader title={`Hello, ${firstName(data.studentName)}`} subtitle={placement} />

      {data.lowAttendance && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="Your attendance is below the required level. Please attend classes regularly."
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <StatCard
            label="Attendance this year"
            value={formatPercent(data.attendancePercentage)}
            tint={data.lowAttendance ? 'rose' : 'mint'}
          />
        </Col>
        <Col xs={12} sm={8}>
          <StatCard label="Latest GPA" value={result ? Number(result.gpa).toFixed(2) : '-'} tint="sky" />
        </Col>
        <Col xs={12} sm={8}>
          <Link to="/my/fees">
            <StatCard label="Fees due" value={formatMoney(data.outstandingFees)} tint={owes ? 'peach' : 'lavender'} />
          </Link>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card title="Attendance" extra={<Link to="/my/attendance">View calendar</Link>} style={{ height: '100%' }}>
            <Space direction="vertical" align="center" style={{ width: '100%' }}>
              <Progress
                type="circle"
                percent={Number(data.attendancePercentage)}
                format={(value) => formatPercent(value)}
                strokeColor={data.lowAttendance ? colors.error : colors.success}
              />
              <Text type="secondary">{`Present ${data.daysPresent} · Absent ${data.daysAbsent}`}</Text>
            </Space>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <Card
            title="Latest result"
            extra={result ? <Link to="/my/results">See full result</Link> : undefined}
            style={{ height: '100%' }}
          >
            {result ? (
              <Descriptions column={1} size="small">
                <Descriptions.Item label="Exam">{result.examName}</Descriptions.Item>
                <Descriptions.Item label="GPA">{Number(result.gpa).toFixed(2)}</Descriptions.Item>
                <Descriptions.Item label="Grade">
                  <GradeTag grade={result.letter} />
                </Descriptions.Item>
                <Descriptions.Item label="Result">
                  {result.passed ? (
                    <Tag color="green" bordered={false}>Passed</Tag>
                  ) : (
                    <Tag color="red" bordered={false}>Failed</Tag>
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Position">{result.position ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="Exam ended">{formatDate(result.endDate)}</Descriptions.Item>
              </Descriptions>
            ) : (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="Results appear here when they are published."
              />
            )}
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          <NoticeList notices={data.notices} />
        </Col>
      </Row>
    </>
  );
}
