import type { ReactElement, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Alert, Button, Card, Col, Empty, Row, Spin, Table } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useAuth } from '../../auth/AuthContext';
import { errorMessage } from '../../api/client';
import { dashboardKeys, getAdminDashboard, type RecentPayment } from '../../api/dashboard';
import { PAYMENT_METHOD_LABELS } from '../../api/fees';
import { PageHeader, StatCard } from '../../components/common';
import { chartPalette, colors } from '../../theme';
import { formatDate, formatDateTime, formatMoney, formatPercent } from '../../utils/format';
import NoticeList from './NoticeList';
import { firstName, greeting } from './greeting';

const CHART_HEIGHT = 260;
const axisTick = { fontSize: 12, fill: colors.slate };
const BAR_RADIUS: [number, number, number, number] = [4, 4, 0, 0];

/** A card holding one chart, or an empty message when there is nothing to draw. */
function ChartCard({ title, empty, emptyText, children }: {
  title: ReactNode;
  empty: boolean;
  emptyText?: string;
  children: ReactElement;
}) {
  return (
    <Card title={title} style={{ height: '100%' }}>
      {empty ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} style={{ paddingBlock: 48 }} />
      ) : (
        <div style={{ width: '100%', height: CHART_HEIGHT }}>
          <ResponsiveContainer width="100%" height="100%">
            {children}
          </ResponsiveContainer>
        </div>
      )}
    </Card>
  );
}

const paymentColumns: ColumnsType<RecentPayment> = [
  { title: 'Student', dataIndex: 'studentName' },
  { title: 'Invoice', dataIndex: 'invoiceNo' },
  { title: 'Amount', dataIndex: 'amount', align: 'right', render: (value: number) => formatMoney(value) },
  { title: 'Method', dataIndex: 'method', render: (value: RecentPayment['method']) => PAYMENT_METHOD_LABELS[value] },
  { title: 'When', dataIndex: 'paidAt', render: (value: string) => formatDateTime(value) },
];

export default function AdminDashboardView() {
  const { user } = useAuth();
  const query = useQuery({ queryKey: dashboardKeys.admin, queryFn: getAdminDashboard });

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
  const trend = data.attendanceTrend.map((point) => ({ ...point, label: formatDate(point.date).slice(0, 6) }));
  const anyGrades = data.gradeDistribution.some((grade) => grade.value > 0);

  return (
    <>
      <PageHeader
        title={`Good ${greeting()}, ${firstName(user?.fullName)}`}
        subtitle={`Academic year ${data.academicYear ?? '-'} at a glance`}
      />

      {data.fees.overdueInvoices > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message={`${data.fees.overdueInvoices} invoices are overdue`}
          action={
            <Link to="/fees">
              <Button size="small">Review</Button>
            </Link>
          }
        />
      )}

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} lg={6}>
          <StatCard label="Active students" value={data.counts.students} tint="sky"
            hint={`${data.counts.classes} classes · ${data.counts.subjects} subjects`} />
        </Col>
        <Col xs={12} lg={6}>
          <StatCard label="Teachers" value={data.counts.teachers} tint="mint" />
        </Col>
        <Col xs={12} lg={6}>
          <StatCard label="Attendance today" value={formatPercent(data.todayAttendance)} tint="peach" />
        </Col>
        <Col xs={12} lg={6}>
          <StatCard label="Fees collected" value={formatMoney(data.fees.totalCollected)} tint="lavender"
            hint={formatPercent(data.fees.collectionRate)} />
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard title="Attendance trend (last 14 days)" empty={trend.length === 0}
            emptyText="No attendance recorded yet">
            <LineChart data={trend} margin={{ top: 8, right: 16, left: -8, bottom: 0 }}>
              <CartesianGrid stroke={colors.hairline} vertical={false} />
              <XAxis dataKey="label" tick={axisTick} tickLine={false} axisLine={{ stroke: colors.hairline }} />
              <YAxis domain={[0, 100]} unit="%" tick={axisTick} tickLine={false} axisLine={false} />
              <Tooltip formatter={(value) => [formatPercent(Number(value)), 'Attendance']} />
              <Line type="monotone" dataKey="percentage" stroke={chartPalette[0]} strokeWidth={2}
                dot={{ r: 4, strokeWidth: 2, fill: '#fff' }} activeDot={{ r: 5 }} />
            </LineChart>
          </ChartCard>
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard title="Fee collection by month" empty={data.monthlyCollection.length === 0}>
            <BarChart data={data.monthlyCollection} margin={{ top: 8, right: 16, left: 8, bottom: 0 }}>
              <CartesianGrid stroke={colors.hairline} vertical={false} />
              <XAxis dataKey="month" tick={axisTick} tickLine={false} axisLine={{ stroke: colors.hairline }} />
              <YAxis tick={axisTick} tickLine={false} axisLine={false} width={56} />
              <Tooltip cursor={{ fill: colors.surface }} formatter={(value) => [formatMoney(Number(value)), 'Collected']} />
              <Bar dataKey="amount" fill={chartPalette[1]} radius={BAR_RADIUS} maxBarSize={40} />
            </BarChart>
          </ChartCard>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} lg={12}>
          <ChartCard title="Students per class" empty={data.classStrength.length === 0}>
            <BarChart data={data.classStrength} margin={{ top: 8, right: 16, left: -8, bottom: 0 }}>
              <CartesianGrid stroke={colors.hairline} vertical={false} />
              <XAxis dataKey="name" tick={axisTick} tickLine={false} axisLine={{ stroke: colors.hairline }} />
              <YAxis allowDecimals={false} tick={axisTick} tickLine={false} axisLine={false} />
              <Tooltip cursor={{ fill: colors.surface }} formatter={(value) => [value, 'Students']} />
              <Bar dataKey="value" fill={chartPalette[0]} radius={BAR_RADIUS} maxBarSize={40} />
            </BarChart>
          </ChartCard>
        </Col>
        <Col xs={24} lg={12}>
          <ChartCard title="Gender split" empty={data.genderSplit.length === 0}>
            <PieChart>
              {/* Copies plain objects: Recharts wants an indexable shape. */}
              <Pie data={data.genderSplit.map((slice) => ({ ...slice }))} dataKey="value" nameKey="name" innerRadius="50%" outerRadius="80%"
                paddingAngle={1} stroke="#fff" strokeWidth={2} label={({ value }) => String(value)}>
                {data.genderSplit.map((entry, index) => (
                  <Cell key={entry.name} fill={chartPalette[index % chartPalette.length]} />
                ))}
              </Pie>
              <Tooltip />
              <Legend />
            </PieChart>
          </ChartCard>
        </Col>
      </Row>

      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24}>
          <ChartCard title={data.latestExam ?? 'Results'} empty={!anyGrades} emptyText="No published results yet">
            <BarChart data={data.gradeDistribution} margin={{ top: 8, right: 16, left: -8, bottom: 0 }}>
              <CartesianGrid stroke={colors.hairline} vertical={false} />
              <XAxis dataKey="name" tick={axisTick} tickLine={false} axisLine={{ stroke: colors.hairline }} />
              <YAxis allowDecimals={false} tick={axisTick} tickLine={false} axisLine={false} />
              <Tooltip cursor={{ fill: colors.surface }} formatter={(value) => [value, 'Students']} />
              <Bar dataKey="value" fill={chartPalette[4]} radius={BAR_RADIUS} maxBarSize={48} />
            </BarChart>
          </ChartCard>
        </Col>
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={14}>
          <Card title="Recent payments" style={{ height: '100%' }}>
            <Table
              rowKey={(row) => `${row.invoiceNo}-${row.paidAt}`}
              columns={paymentColumns}
              dataSource={data.recentPayments}
              pagination={false}
              size="small"
              scroll={{ x: 560 }}
            />
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <NoticeList notices={data.notices} />
        </Col>
      </Row>
    </>
  );
}
