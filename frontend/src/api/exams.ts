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

export interface MarkRow {
  studentId: number;
  rollNo?: number | null;
  admissionNo: string;
  studentName: string;
  marksObtained?: number | null;
  absent: boolean;
}

export interface MarkSheet {
  examId: number;
  examName: string;
  classId: number;
  className: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  fullMarks: number;
  passMarks: number;
  editable: boolean;
  rows: MarkRow[];
}

export interface MarkEntry {
  studentId: number;
  marksObtained: number | null;
  absent: boolean;
}

export interface MarkSheetRequest {
  classId: number;
  subjectId: number;
  entries: MarkEntry[];
}

export interface SubjectResult {
  subjectId: number;
  subjectCode: string;
  subjectName: string;
  fullMarks: number;
  passMarks: number;
  obtained?: number | null;
  absent: boolean;
  letter: string;
  point: number;
  passed: boolean;
}

export interface StudentResultRow {
  studentId: number;
  rollNo?: number | null;
  admissionNo: string;
  studentName: string;
  totalObtained: number;
  totalFullMarks: number;
  percentage: number;
  gpa: number;
  letter: string;
  passed: boolean;
  position?: number | null;
  subjects: SubjectResult[];
}

export interface ClassResult {
  examId: number;
  examName: string;
  status: ExamStatus;
  classId: number;
  className: string;
  gradingScheme: string;
  passed: number;
  failed: number;
  students: StudentResultRow[];
}

export interface MyExamResult {
  examId: number;
  examName: string;
  endDate: string;
  gpa: number;
  letter: string;
  passed: boolean;
  position?: number | null;
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

export async function getMarkSheet(
  examId: number,
  classId: number,
  subjectId: number,
): Promise<MarkSheet> {
  const { data } = await api.get<MarkSheet>(`/exams/${examId}/marks`, {
    params: { classId, subjectId },
  });
  return data;
}

export async function saveMarkSheet(
  examId: number,
  request: MarkSheetRequest,
): Promise<MarkSheet> {
  const { data } = await api.put<MarkSheet>(`/exams/${examId}/marks`, request);
  return data;
}

export async function publishExam(id: number): Promise<ExamResponse> {
  const { data } = await api.post<ExamResponse>(`/exams/${id}/publish`);
  return data;
}

export async function unpublishExam(id: number): Promise<ExamResponse> {
  const { data } = await api.post<ExamResponse>(`/exams/${id}/unpublish`);
  return data;
}

export async function getClassResult(examId: number, classId: number): Promise<ClassResult> {
  const { data } = await api.get<ClassResult>(`/exams/${examId}/results`, {
    params: { classId },
  });
  return data;
}

export async function listMyResults(): Promise<MyExamResult[]> {
  const { data } = await api.get<MyExamResult[]>('/me/results');
  return data;
}

export async function getMyExamResult(examId: number): Promise<StudentResultRow> {
  const { data } = await api.get<StudentResultRow>(`/me/results/${examId}`);
  return data;
}
