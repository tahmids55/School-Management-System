import { api } from './client';

export type ExamStatus = 'DRAFT' | 'PUBLISHED';

export interface ExamRequest {
  academicYearId: number;
  name: string;
  /** "YYYY-MM-DD" */
  startDate: string;
  /** "YYYY-MM-DD" */
  endDate: string;
}

export interface ExamResponse {
  id: number;
  academicYearId: number;
  academicYearName: string;
  name: string;
  /** "YYYY-MM-DD" */
  startDate: string;
  /** "YYYY-MM-DD" */
  endDate: string;
  status: ExamStatus;
  publishedAt?: string;
}

export async function listExams(academicYearId?: number): Promise<ExamResponse[]> {
  const { data } = await api.get<ExamResponse[]>('/exams', {
    params: academicYearId ? { academicYearId } : undefined,
  });
  return data;
}

export async function getExam(id: number): Promise<ExamResponse> {
  const { data } = await api.get<ExamResponse>(`/exams/${id}`);
  return data;
}

export async function createExam(request: ExamRequest): Promise<ExamResponse> {
  const { data } = await api.post<ExamResponse>('/exams', request);
  return data;
}

export async function updateExam(id: number, request: ExamRequest): Promise<ExamResponse> {
  const { data } = await api.put<ExamResponse>(`/exams/${id}`, request);
  return data;
}

export async function deleteExam(id: number): Promise<void> {
  await api.delete(`/exams/${id}`);
}
