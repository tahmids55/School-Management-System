import { api } from './client';
import type { MyExamResult } from './exams';
import type { FeeSummary, PaymentMethod } from './fees';
import type { NoticeResponse } from './notices';
import type { TeachingLoad } from './teachers';

// Shapes of GET /dashboard/admin and /dashboard/teacher (F25) and /dashboard/student (F31). The server leaves out null values.

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

/** GET /dashboard/student (F31): the logged-in student's own numbers, attendance from 1 January to today. */
export interface StudentDashboard {
  studentName: string;
  /** Missing when the student has no class. */
  className?: string;
  rollNo?: number;
  attendancePercentage: number;
  lowAttendance: boolean;
  /** Present + late. */
  daysPresent: number;
  daysAbsent: number;
  /** Missing until a result of the student's class is published. */
  latestResult?: MyExamResult;
  outstandingFees: number;
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

export async function getStudentDashboard(): Promise<StudentDashboard> {
  const { data } = await api.get<StudentDashboard>('/dashboard/student');
  return data;
}

export const dashboardKeys = {
  all: ['dashboard'] as const,
  admin: ['dashboard', 'admin'] as const,
  teacher: ['dashboard', 'teacher'] as const,
  student: ['dashboard', 'student'] as const,
};
