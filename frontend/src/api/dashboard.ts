import { api } from './client';
import type { FeeSummary, PaymentMethod } from './fees';
import type { NoticeResponse } from './notices';
import type { TeachingLoad } from './teachers';

// Shapes of GET /dashboard/admin and /dashboard/teacher (F25). The server leaves out null values.

export interface Counts {
  students: number;
  teachers: number;
  classes: number;
  subjects: number;
}

export interface TrendPoint {
  date: string;
  percentage: number;
}

export interface MonthAmount {
  /** "Nov 2026" */
  month: string;
  amount: number;
}

export interface NameValue {
  name: string;
  value: number;
}

export interface RecentPayment {
  studentName: string;
  invoiceNo: string;
  amount: number;
  method: PaymentMethod;
  paidAt: string;
}

export interface AdminDashboard {
  /** Missing when no academic year is current. */
  academicYear?: string;
  counts: Counts;
  /** Missing when no attendance was taken today. */
  todayAttendance?: number;
  attendanceTrend: TrendPoint[];
  fees: FeeSummary;
  monthlyCollection: MonthAmount[];
  classStrength: NameValue[];
  genderSplit: NameValue[];
  /** Missing when no exam is published. */
  latestExam?: string;
  gradeDistribution: NameValue[];
  recentPayments: RecentPayment[];
  notices: NoticeResponse[];
}

export interface ClassToday {
  classId: number;
  className: string;
  students: number;
  attendanceTaken: boolean;
}

export interface TeacherDashboard {
  teacherName: string;
  teachingLoad: TeachingLoad;
  classesToday: ClassToday[];
  notices: NoticeResponse[];
}

export async function getAdminDashboard(): Promise<AdminDashboard> {
  const { data } = await api.get<AdminDashboard>('/dashboard/admin');
  return data;
}

export async function getTeacherDashboard(): Promise<TeacherDashboard> {
  const { data } = await api.get<TeacherDashboard>('/dashboard/teacher');
  return data;
}

export const dashboardKeys = {
  all: ['dashboard'] as const,
  admin: ['dashboard', 'admin'] as const,
  teacher: ['dashboard', 'teacher'] as const,
};
