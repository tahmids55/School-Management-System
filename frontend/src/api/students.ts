import { api } from './client';
import type { PageResponse } from './audit';
import { saveBlob } from '../utils/saveBlob';

// ---------- Students and CSV export (F13) ----------

export type Gender = 'MALE' | 'FEMALE' | 'OTHER';
export type StudentStatus = 'ACTIVE' | 'INACTIVE' | 'GRADUATED' | 'TRANSFERRED';

export const STUDENT_STATUSES: StudentStatus[] = ['ACTIVE', 'INACTIVE', 'GRADUATED', 'TRANSFERRED'];

/** One row of the student table or a class roster. */
export interface StudentSummary {
  id: number;
  admissionNo: string;
  fullName: string;
  rollNo?: number | null;
  classId?: number | null;
  /** "Class 6 - A"; missing = not in a class */
  className?: string | null;
  gender: Gender;
  status: StudentStatus;
  guardianPhone: string;
}

export interface Student {
  id: number;
  admissionNo: string;
  firstName: string;
  lastName: string;
  fullName: string;
  gender: Gender;
  dateOfBirth: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  guardianName: string;
  guardianPhone: string;
  guardianRelation: string;
  classId?: number | null;
  className?: string | null;
  rollNo?: number | null;
  status: StudentStatus;
  admissionDate: string;
  /** The student's login; missing = no login. */
  username?: string | null;
}

export interface StudentRequest {
  admissionNo: string;
  firstName: string;
  lastName: string;
  gender: Gender;
  /** "YYYY-MM-DD" */
  dateOfBirth: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  guardianName: string;
  guardianPhone: string;
  guardianRelation: string;
  /** null = not in a class (then the roll is ignored) */
  classId?: number | null;
  /** null = next free roll in the class */
  rollNo?: number | null;
  /** "YYYY-MM-DD" */
  admissionDate: string;
  createAccount?: boolean;
}

export interface CreatedStudentResponse {
  student: Student;
  /** Present only when a login was created (F14). */
  account?: { username: string; temporaryPassword: string } | null;
}

export interface StudentSearch {
  q?: string;
  classId?: number;
  status?: StudentStatus;
  page?: number;
  size?: number;
}

/** GET /students: one page, ordered by admission no. Empty filters are left out. */
export async function listStudents(search: StudentSearch = {}): Promise<PageResponse<StudentSummary>> {
  const params = {
    q: search.q?.trim() || undefined,
    classId: search.classId,
    status: search.status,
    page: search.page ?? 0,
    size: search.size ?? 20,
  };
  const { data } = await api.get<PageResponse<StudentSummary>>('/students', { params });
  return data;
}

/** GET /students/{id} */
export async function getStudent(id: number): Promise<Student> {
  const { data } = await api.get<Student>(`/students/${id}`);
  return data;
}

/** POST /students (ADMIN). New students are ACTIVE. */
export async function createStudent(request: StudentRequest): Promise<CreatedStudentResponse> {
  const { data } = await api.post<CreatedStudentResponse>('/students', request);
  return data;
}

/** PUT /students/{id} (ADMIN). The admission no never changes. */
export async function updateStudent(id: number, request: StudentRequest): Promise<Student> {
  const { data } = await api.put<Student>(`/students/${id}`, request);
  return data;
}

/** PATCH /students/{id}/status (ADMIN). A non-active student cannot log in. */
export async function changeStudentStatus(id: number, status: StudentStatus): Promise<Student> {
  const { data } = await api.patch<Student>(`/students/${id}/status`, { status });
  return data;
}

/** GET /students/export (ADMIN): downloads students.csv with the same class/status filters. */
export async function exportStudents(filters: { classId?: number; status?: StudentStatus }): Promise<void> {
  const response = await api.get<Blob>('/students/export', { params: filters, responseType: 'blob' });
  saveBlob(response.data, response.headers['content-disposition'] as string | undefined, 'students.csv');
}

/** GET /classes/{id}/students: the active students of a class, ordered by roll. */
export async function listClassStudents(classId: number): Promise<StudentSummary[]> {
  const { data } = await api.get<StudentSummary[]>(`/classes/${classId}/students`);
  return data;
}

/** React Query keys. Invalidate studentKeys.all after any student change. */
export const studentKeys = {
  all: ['students'] as const,
  list: (search: StudentSearch) => ['students', 'list', search] as const,
  detail: (id: number) => ['students', 'detail', id] as const,
  roster: (classId: number) => ['students', 'roster', classId] as const,
};
