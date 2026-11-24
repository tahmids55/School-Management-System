import { api } from './client';

// ---------- Academic years (F07) ----------

export interface AcademicYear {
  id: number;
  name: string;
  /** "YYYY-MM-DD" */
  startDate: string;
  /** "YYYY-MM-DD" */
  endDate: string;
  current: boolean;
}

export interface AcademicYearRequest {
  name: string;
  startDate: string;
  endDate: string;
}

/** GET /academic-years, newest first. */
export async function listAcademicYears(): Promise<AcademicYear[]> {
  const { data } = await api.get<AcademicYear[]>('/academic-years');
  return data;
}

/** GET /academic-years/{id} */
export async function getAcademicYear(id: number): Promise<AcademicYear> {
  const { data } = await api.get<AcademicYear>(`/academic-years/${id}`);
  return data;
}

/** GET /academic-years/current (404 when no year is current). */
export async function getCurrentAcademicYear(): Promise<AcademicYear> {
  const { data } = await api.get<AcademicYear>('/academic-years/current');
  return data;
}

/** POST /academic-years (ADMIN). The first year becomes the current one. */
export async function createAcademicYear(request: AcademicYearRequest): Promise<AcademicYear> {
  const { data } = await api.post<AcademicYear>('/academic-years', request);
  return data;
}

/** PUT /academic-years/{id} (ADMIN) */
export async function updateAcademicYear(id: number, request: AcademicYearRequest): Promise<AcademicYear> {
  const { data } = await api.put<AcademicYear>(`/academic-years/${id}`, request);
  return data;
}

/** POST /academic-years/{id}/activate (ADMIN): makes it the only current year. */
export async function activateAcademicYear(id: number): Promise<AcademicYear> {
  const { data } = await api.post<AcademicYear>(`/academic-years/${id}/activate`);
  return data;
}

/** DELETE /academic-years/{id} (ADMIN). Not allowed for the current year or a year with classes or exams. */
export async function deleteAcademicYear(id: number): Promise<void> {
  await api.delete(`/academic-years/${id}`);
}

// ---------- Subjects (F07) ----------

export interface Subject {
  id: number;
  code: string;
  name: string;
  fullMarks: number;
  passMarks: number;
}

export interface SubjectRequest {
  code: string;
  name: string;
  fullMarks: number;
  passMarks: number;
}

/** GET /subjects, ordered by code. */
export async function listSubjects(): Promise<Subject[]> {
  const { data } = await api.get<Subject[]>('/subjects');
  return data;
}

/** GET /subjects/{id} */
export async function getSubject(id: number): Promise<Subject> {
  const { data } = await api.get<Subject>(`/subjects/${id}`);
  return data;
}

/** POST /subjects (ADMIN). The server trims the code and makes it upper-case. */
export async function createSubject(request: SubjectRequest): Promise<Subject> {
  const { data } = await api.post<Subject>('/subjects', request);
  return data;
}

/** PUT /subjects/{id} (ADMIN) */
export async function updateSubject(id: number, request: SubjectRequest): Promise<Subject> {
  const { data } = await api.put<Subject>(`/subjects/${id}`, request);
  return data;
}

/** DELETE /subjects/{id} (ADMIN) */
export async function deleteSubject(id: number): Promise<void> {
  await api.delete(`/subjects/${id}`);
}

/** React Query keys, shared so every screen refreshes the same lists. */
export const academicKeys = {
  years: ['academic-years'] as const,
  subjects: ['subjects'] as const,
};
