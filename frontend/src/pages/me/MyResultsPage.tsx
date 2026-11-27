import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, List, Row, Space, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { DownloadOutlined, FilePdfOutlined } from '@ant-design/icons';
import { errorMessage } from '../../api/client';
import {
  downloadReportCard,
  getMyExamResult,
  listMyResults,
  type MyExamResult,
  type ReportFormat,
  type SubjectResult,
} from '../../api/exams';
import { GradeTag, PageHeader, StatCard } from '../../components/common';
import { feedback } from '../../utils/feedback';
import { formatDate } from '../../utils/format';

const { Text } = Typography;

const subjectColumns: ColumnsType<SubjectResult> = [
  {
    title: 'Subject',
    key: 'subject',
    render: (_, row) => `${row.subjectCode} – ${row.subjectName}`,
  },
  {
    title: 'Marks',
    key: 'marks',
    width: 140,
    render: (_, row) =>
      row.absent ? (
        <Tag color="red" bordered={false}>
          Absent
        </Tag>
      ) : (
        `${row.obtained ?? 0} / ${row.fullMarks}`
      ),
  },
  {
    title: 'Grade',
    dataIndex: 'letter',
    key: 'letter',
    width: 90,
    render: (letter: string) => <GradeTag grade={letter} />,
  },
  {
    title: 'Point',
    dataIndex: 'point',
    key: 'point',
    width: 90,
    render: (point: number) => Number(point).toFixed(2),
  },
];

export default function MyResultsPage() {
  const [selectedExamId, setSelectedExamId] = useState<number | undefined>(undefined);
  const [downloadingFormat, setDownloadingFormat] = useState<ReportFormat | null>(null);

  const handleDownload = async (studentId: number, format: ReportFormat) => {
    if (!selectedExamId) return;
    setDownloadingFormat(format);
    try {
      await downloadReportCard(selectedExamId, studentId, format);
    } catch (err) {
      feedback.message.error(errorMessage(err));
    } finally {
      setDownloadingFormat(null);
    }
  };

  const listQuery = useQuery({
    queryKey: ['my-results'],
    queryFn: listMyResults,
  });

  const exams = listQuery.data ?? [];

  useEffect(() => {
    const first = exams[0];
    if (selectedExamId === undefined && first) {
      setSelectedExamId(first.examId);
    }
  }, [exams, selectedExamId]);

  const detailQuery = useQuery({
    queryKey: ['my-results', selectedExamId],
    queryFn: () => getMyExamResult(selectedExamId!),
    enabled: selectedExamId !== undefined,
  });

  const detail = detailQuery.data;

  return (
    <div>
      <PageHeader title="My results" subtitle="Published exam results" />

      {listQuery.isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : listQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(listQuery.error)}
          action={
            <Button size="small" onClick={() => listQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : exams.length === 0 ? (
        <Card>
          <Empty description="No published results yet" />
        </Card>
      ) : (
        <Row gutter={[16, 16]}>
          <Col xs={24} md={8}>
            <Card title="Exams" styles={{ body: { padding: 0 } }}>
              <List<MyExamResult>
                dataSource={exams}
                renderItem={(item) => {
                  const active = item.examId === selectedExamId;
                  return (
                    <List.Item
                      onClick={() => setSelectedExamId(item.examId)}
                      style={{
                        cursor: 'pointer',
                        padding: '12px 16px',
                        background: active ? '#f0f5ff' : undefined,
                      }}
                    >
                      <List.Item.Meta
                        title={<Text strong>{item.examName}</Text>}
                        description={formatDate(item.endDate)}
                      />
                      <Space size={8}>
                        <Text strong>{Number(item.gpa).toFixed(2)}</Text>
                        <GradeTag grade={item.letter} />
                      </Space>
                    </List.Item>
                  );
                }}
              />
            </Card>
          </Col>

          <Col xs={24} md={16}>
            {detailQuery.isLoading ? (
              <Card>
                <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
                  <Spin />
                </div>
              </Card>
            ) : detailQuery.isError ? (
              <Alert
                type="error"
                showIcon
                message={errorMessage(detailQuery.error)}
                action={
                  <Button size="small" onClick={() => detailQuery.refetch()}>
                    Retry
                  </Button>
                }
              />
            ) : detail ? (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Row gutter={[12, 12]}>
                  <Col xs={12} sm={6}>
                    <StatCard label="GPA" value={Number(detail.gpa).toFixed(2)} tint="sky" />
                  </Col>
                  <Col xs={12} sm={6}>
                    <StatCard label="Grade" value={detail.letter} tint={detail.passed ? 'mint' : 'rose'} />
                  </Col>
                  <Col xs={12} sm={6}>
                    <StatCard
                      label="Total"
                      value={`${detail.totalObtained} / ${detail.totalFullMarks}`}
                      tint="lavender"
                    />
                  </Col>
                  <Col xs={12} sm={6}>
                    <StatCard
                      label="Position"
                      value={detail.position ? `#${detail.position}` : '-'}
                      tint="peach"
                    />
                  </Col>
                </Row>

                <Card
                  styles={{ body: { padding: 0 } }}
                  extra={
                    <Space wrap size={8}>
                      <Button
                        size="small"
                        type="primary"
                        icon={<FilePdfOutlined />}
                        loading={downloadingFormat === 'PDF'}
                        onClick={() => handleDownload(detail.studentId, 'PDF')}
                      >
                        Report card (PDF)
                      </Button>
                      <Button
                        size="small"
                        icon={<DownloadOutlined />}
                        loading={downloadingFormat === 'CSV'}
                        onClick={() => handleDownload(detail.studentId, 'CSV')}
                      >
                        CSV
                      </Button>
                    </Space>
                  }
                >
                  <Table<SubjectResult>
                    rowKey="subjectId"
                    columns={subjectColumns}
                    dataSource={detail.subjects}
                    pagination={false}
                    scroll={{ x: 480 }}
                  />
                </Card>
              </Space>
            ) : null}
          </Col>
        </Row>
      )}
    </div>
  );
}
