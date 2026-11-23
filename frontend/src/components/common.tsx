import type { ReactNode } from 'react';
import { Space, Spin, Tag, Typography } from 'antd';
import { colors } from '../theme';
import { feedback } from '../utils/feedback';
import { humanize } from '../utils/format';

const { Title, Text, Paragraph } = Typography;

// ---------- PageHeader ----------

interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  /** Buttons on the right (they wrap under the title on phones). */
  actions?: ReactNode;
}

export function PageHeader({ title, subtitle, actions }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        <Title level={3} style={{ margin: 0 }}>
          {title}
        </Title>
        {subtitle && <Text type="secondary">{subtitle}</Text>}
      </div>
      {actions && <Space wrap>{actions}</Space>}
    </div>
  );
}

// ---------- StatCard ----------

export type StatTint = 'sky' | 'mint' | 'peach' | 'lavender' | 'rose' | 'yellow';

const tintColors: Record<StatTint, string> = {
  sky: colors.tintSky,
  mint: colors.tintMint,
  peach: colors.tintPeach,
  lavender: colors.tintLavender,
  rose: colors.tintRose,
  yellow: colors.tintYellow,
};

interface StatCardProps {
  label: ReactNode;
  value: ReactNode;
  tint?: StatTint;
  hint?: ReactNode;
  icon?: ReactNode;
}

export function StatCard({ label, value, tint = 'sky', hint, icon }: StatCardProps) {
  return (
    <div className="stat-card" style={{ background: tintColors[tint] }}>
      <div className="stat-card-label">
        <span>{label}</span>
        {icon}
      </div>
      <div className="stat-card-value">{value}</div>
      {hint && <div className="stat-card-hint">{hint}</div>}
    </div>
  );
}

// ---------- StatusTag ----------

const statusColors: Record<string, string> = {
  ACTIVE: 'green',
  PUBLISHED: 'green',
  PAID: 'green',
  PRESENT: 'green',
  UNPAID: 'orange',
  PARTIAL: 'gold',
  LATE: 'gold',
  ON_LEAVE: 'gold',
  EXCUSED: 'blue',
  GRADUATED: 'blue',
  TRANSFERRED: 'purple',
  ADMIN: 'purple',
  TEACHER: 'cyan',
  STUDENT: 'geekblue',
  ABSENT: 'red',
  OVERDUE: 'red',
};

/** Any enum value from the API, coloured and readable: "ON_LEAVE" -> gold "On leave". */
export function StatusTag({ value }: { value: string | null | undefined }) {
  if (!value) return null;
  return (
    <Tag color={statusColors[value.toUpperCase()] ?? 'default'} bordered={false}>
      {humanize(value)}
    </Tag>
  );
}

// ---------- GradeTag ----------

const gradeColors: Record<string, string> = {
  'A+': 'green',
  A: 'green',
  'A-': 'cyan',
  B: 'blue',
  C: 'gold',
  D: 'orange',
  F: 'red',
};

/** Letter grade of the Bangladesh scale (A+ ... F). */
export function GradeTag({ grade }: { grade: string | null | undefined }) {
  if (!grade) return <Text type="secondary">-</Text>;
  return (
    <Tag color={gradeColors[grade] ?? 'default'} bordered={false} style={{ fontWeight: 600 }}>
      {grade}
    </Tag>
  );
}

// ---------- Loading ----------

export function FullPageSpinner() {
  return (
    <div className="full-page-center">
      <Spin size="large" />
    </div>
  );
}

// ---------- Temporary password ----------

/**
 * Shows a new account's username and temporary password exactly once.
 * The server never returns that password again, so both values can be copied.
 */
export function showTemporaryPassword(username: string, temporaryPassword: string): void {
  feedback.modal.success({
    title: 'Temporary password',
    width: 440,
    content: (
      <div>
        <Paragraph style={{ marginBottom: 4 }}>
          Username: <Text strong copyable>{username}</Text>
        </Paragraph>
        <Paragraph>
          Password: <Text strong code copyable>{temporaryPassword}</Text>
        </Paragraph>
        <Text type="secondary">Shown only once. Share it securely; the user must change it at first login.</Text>
      </div>
    ),
  });
}
