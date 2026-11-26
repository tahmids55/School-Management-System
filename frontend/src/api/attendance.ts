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

export const attendanceKeys = {
  all: ['attendance'] as const,
  sheet: (classId: number, date: string) => ['attendance', 'sheet', classId, date] as const,
};
