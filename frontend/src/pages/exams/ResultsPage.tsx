import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, Row, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { classKeys, listClasses } from '../../api/classes';
import { errorMessage } from '../../api/client';
import {
  getClassResult,
  listExams,
  type StudentResultRow,
  type SubjectResult,
} from '../../api/exams';
import { GradeTag, PageHeader, StatCard, StatusTag } from '../../components/common';
import { formatPercent } from '../../utils/format';

const { Text } = Typography;

const subjectColumns: ColumnsType<SubjectResult> = [
  {
    title: 'Subject',
    key: 'subject',
    render: (_, row) => `${row.subjectCode} – ${row.subjectName}`,
  },
  {
    title: 'Obtained',
    key: 'obtained',
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

export default function ResultsPage() {
  const [examId, setExamId] = useState<number | undefined>(undefined);
  const [classId, setClassId] = useState<number | undefined>(undefined);

  const examsQuery = useQuery({
    queryKey: ['exams', 'current'],
    queryFn: () => listExams(),
  });

  const selectedExam = useMemo(
    () => (examsQuery.data ?? []).find((e) => e.id === examId),
    [examsQuery.data, examId],
  );

  const classesQuery = useQuery({
    queryKey: classKeys.list(selectedExam?.academicYearId),
    queryFn: () => listClasses(selectedExam?.academicYearId),
    enabled: selectedExam !== undefined,
  });

  const resultQuery = useQuery({
    queryKey: ['class-result', examId, classId],
    queryFn: () => getClassResult(examId!, classId!),
    enabled: examId !== undefined && classId !== undefined,
  });

  const result = resultQuery.data;
  const totalStudents = result ? result.passed + result.failed : 0;
  const passRate = totalStudents > 0 && result ? (result.passed / totalStudents) * 100 : 0;

  const meritColumns: ColumnsType<StudentResultRow> = [
    {
      title: 'Pos.',
      dataIndex: 'position',
      key: 'position',
      width: 75,
      render: (pos?: number | null) => (pos ? <Text strong>#{pos}</Text> : <Text type="secondary">-</Text>),
    },
    {
      title: 'Roll',
      dataIndex: 'rollNo',
      key: 'rollNo',
      width: 75,
      render: (roll?: number | null) => roll ?? '-',
    },
    {
      title: 'Student',
      key: 'student',
      render: (_, row) => (
        <div>
          <Text strong>{row.studentName}</Text>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {row.admissionNo}
            </Text>
          </div>
        </div>
      ),
    },
    {
      title: 'Total',
      key: 'total',
      width: 120,
      render: (_, row) => `${row.totalObtained} / ${row.totalFullMarks}`,
    },
    {
      title: '%',
      dataIndex: 'percentage',
      key: 'percentage',
      width: 90,
      render: (value: number) => formatPercent(value),
    },
    {
      title: 'GPA',
      dataIndex: 'gpa',
      key: 'gpa',
      width: 85,
      render: (value: number) => <Text strong>{Number(value).toFixed(2)}</Text>,
    },
    {
      title: 'Grade',
      dataIndex: 'letter',
      key: 'letter',
      width: 90,
      render: (letter: string) => <GradeTag grade={letter} />,
    },
  ];

  return (
    <div>
      <PageHeader title="Results" subtitle="Merit list, GPA and report cards" />

      <Card style={{ marginBottom: 16 }}>
        <Space wrap size={[12, 12]} style={{ width: '100%' }}>
          <Select<number>
            placeholder="Exam"
            aria-label="Exam"
            value={examId}
            loading={examsQuery.isLoading}
            style={{ width: 240, maxWidth: '100%' }}
            options={(examsQuery.data ?? []).map((e) => ({
              value: e.id,
              label: `${e.name} (${e.academicYearName})`,
            }))}
            onChange={(value) => {
              setExamId(value);
              setClassId(undefined);
            }}
          />

          <Select<number>
            placeholder="Class"
            aria-label="Class"
            value={classId}
            disabled={!examId}
            loading={classesQuery.isLoading}
            style={{ width: 200, maxWidth: '100%' }}
            options={(classesQuery.data ?? []).map((c) => ({
              value: c.id,
              label: c.displayName,
            }))}
            onChange={(value) => setClassId(value)}
          />
        </Space>
      </Card>

      {examId === undefined || classId === undefined ? (
        <Card>
          <Empty description="Choose an exam and a class" />
        </Card>
      ) : resultQuery.isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : resultQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={errorMessage(resultQuery.error)}
          action={
            <Button size="small" onClick={() => resultQuery.refetch()}>
              Retry
            </Button>
          }
        />
      ) : result ? (
        <Space direction="vertical" size={16} style={{ width: '100%' }}>
          <Row gutter={[16, 16]}>
            <Col xs={12} sm={6}>
              <StatCard label="Students" value={totalStudents} tint="sky" />
            </Col>
            <Col xs={12} sm={6}>
              <StatCard label="Passed" value={result.passed} tint="mint" />
            </Col>
            <Col xs={12} sm={6}>
              <StatCard label="Failed" value={result.failed} tint="rose" />
            </Col>
            <Col xs={12} sm={6}>
              <StatCard label="Pass rate" value={formatPercent(passRate)} tint="lavender" />
            </Col>
          </Row>

          <Card
            styles={{ body: { padding: 0 } }}
            title={
              <Space wrap>
                <span>
                  {result.examName} · {result.className}
                </span>
                <StatusTag value={result.status} />
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {result.gradingScheme}
                </Text>
              </Space>
            }
          >
            <Table<StudentResultRow>
              rowKey="studentId"
              columns={meritColumns}
              dataSource={result.students}
              pagination={false}
              scroll={{ x: 680 }}
              locale={{
                emptyText: <Empty description="No students in this class" />,
              }}
              expandable={{
                expandedRowRender: (record) => (
                  <Table<SubjectResult>
                    rowKey="subjectId"
                    columns={subjectColumns}
                    dataSource={record.subjects}
                    pagination={false}
                    size="small"
                    scroll={{ x: 460 }}
                  />
                ),
              }}
            />
          </Card>
        </Space>
      ) : null}
    </div>
  );
}
