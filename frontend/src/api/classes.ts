import { api } from './client';

// ---------- Classes and sections (F08) ----------

export interface SchoolClass {
  id: number;
  academicYearId: number;
  academicYearName: string;
  name: string;
  section: string;
  /** "Class 6 - A" */
  displayName: string;
  room?: string | null;
  capacity: number;
  /** Filled by F16 (teacher assignment); missing until then. */
  classTeacherId?: number | null;
  classTeacherName?: string | null;
  studentCount: number;
}

export interface ClassRequest {
  academicYearId: number;
  name: string;
  section: string;
  room?: string | null;
  capacity: number;
  classTeacherId?: number | null;
}

export interface ClassSubject {
  id: number;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  fullMarks: number;
  passMarks: number;
  teacherId?: number | null;
  teacherName?: string | null;
}

export interface ClassSubjectRequest {
  subjectId: number;
  teacherId?: number | null;
}

/** GET /classes (ADMIN, TEACHER). Without a year: the current year's classes (empty when none is current). */
export async function listClasses(academicYearId?: number): Promise<SchoolClass[]> {
  const { data } = await api.get<SchoolClass[]>('/classes', { params: { academicYearId } });
  return data;
}

/** GET /classes/{id} */
export async function getClass(id: number): Promise<SchoolClass> {
  const { data } = await api.get<SchoolClass>(`/classes/${id}`);
  return data;
}

/** POST /classes (ADMIN). The server trims the name and upper-cases the section. */
export async function createClass(request: ClassRequest): Promise<SchoolClass> {
  const { data } = await api.post<SchoolClass>('/classes', request);
  return data;
}

/** PUT /classes/{id} (ADMIN) */
export async function updateClass(id: number, request: ClassRequest): Promise<SchoolClass> {
  const { data } = await api.put<SchoolClass>(`/classes/${id}`, request);
  return data;
}

/** DELETE /classes/{id} (ADMIN). Refused while students are enrolled. */
export async function deleteClass(id: number): Promise<void> {
  await api.delete(`/classes/${id}`);
}

/** GET /classes/{id}/subjects (ADMIN, TEACHER). Ordered by subject code. */
export async function listClassSubjects(classId: number): Promise<ClassSubject[]> {
  const { data } = await api.get<ClassSubject[]>(`/classes/${classId}/subjects`);
  return data;
}

/** PUT /classes/{id}/subjects (ADMIN). Adds or updates the subject's teacher (null allowed). */
export async function assignClassSubject(
  classId: number,
  request: ClassSubjectRequest,
): Promise<ClassSubject> {
  const { data } = await api.put<ClassSubject>(`/classes/${classId}/subjects`, request);
  return data;
}

/** DELETE /classes/{id}/subjects/{subjectId} (ADMIN). */
export async function removeClassSubject(classId: number, subjectId: number): Promise<void> {
  await api.delete(`/classes/${classId}/subjects/${subjectId}`);
}

/** React Query keys. Invalidate classKeys.all to refresh every class list. */
export const classKeys = {
  all: ['classes'] as const,
  /** undefined = "the current year" */
  list: (academicYearId?: number) => ['classes', academicYearId ?? 'current'] as const,
  subjects: (classId: number) => ['classes', classId, 'subjects'] as const,
};
