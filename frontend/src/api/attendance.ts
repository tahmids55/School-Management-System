import { api } from './client';

// ---------- Daily attendance (F19) ----------

export type AttendanceStatus = 'PRESENT' | 'ABSENT' | 'LATE' | 'EXCUSED';

export interface SheetRow {
  studentId: number;
  rollNo?: number | null;
  admissionNo: string;
  studentName: string;
  /** Missing when the student has no record that day. */
  status?: AttendanceStatus | null;
  remarks?: string | null;
}

export interface AttendanceSheet {
  classId: number;
  className: string;
  /** "YYYY-MM-DD" */
  date: string;
  /** True once the day was saved for this class. */
  taken: boolean;
  rows: SheetRow[];
}

export interface Entry {
  studentId: number;
  status: AttendanceStatus;
  remarks?: string | null;
}

export interface SaveSheetRequest {
  classId: number;
  /** "YYYY-MM-DD" */
  date: string;
  entries: Entry[];
}

export async function getAttendanceSheet(classId: number, date: string): Promise<AttendanceSheet> {
  const { data } = await api.get<AttendanceSheet>('/attendance/sheet', { params: { classId, date } });
  return data;
}

export async function saveAttendanceSheet(request: SaveSheetRequest): Promise<AttendanceSheet> {
  const { data } = await api.put<AttendanceSheet>('/attendance/sheet', request);
  return data;
}

// ---------- Reports (F20) ----------

export interface StudentAttendance {
  studentId: number;
  rollNo?: number | null;
  studentName: string;
  present: number;
  absent: number;
  late: number;
  excused: number;
  /** (present + late) / (present + late + absent) x 100, one decimal; 100 when nothing counted. */
  percentage: number;
  /** Strictly below lowThreshold. */
  low: boolean;
}

export interface ClassReport {
  classId: number;
  className: string;
  from: string;
  to: string;
  daysTaken: number;
  lowThreshold: number;
  students: StudentAttendance[];
}

export interface DayRecord {
  /** "YYYY-MM-DD" */
  date: string;
  status: AttendanceStatus;
  remarks?: string | null;
}

export interface StudentSummary {
  studentId: number;
  studentName: string;
  from: string;
  to: string;
  present: number;
  absent: number;
  late: number;
  excused: number;
  percentage: number;
  low: boolean;
  /** Newest first. */
  records: DayRecord[];
}

export async function getClassReport(classId: number, from: string, to: string): Promise<ClassReport> {
  const { data } = await api.get<ClassReport>('/attendance/report', { params: { classId, from, to } });
  return data;
}

export async function getStudentAttendance(studentId: number, from: string, to: string): Promise<StudentSummary> {
  const { data } = await api.get<StudentSummary>(`/attendance/students/${studentId}`, { params: { from, to } });
  return data;
}

export const attendanceKeys = {
  all: ['attendance'] as const,
  sheet: (classId: number, date: string) => ['attendance', 'sheet', classId, date] as const,
  report: (classId: number, from: string, to: string) => ['attendance', 'report', classId, from, to] as const,
  student: (studentId: number, from: string, to: string) => ['attendance', 'student', studentId, from, to] as const,
};
