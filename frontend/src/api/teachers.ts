import { api } from './client';

export type TeacherStatus = 'ACTIVE' | 'ON_LEAVE' | 'RESIGNED';

export interface Teacher {
  id: number;
  employeeNo: string;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string;
  phone: string;
  designation: string;
  joiningDate: string;
  status: TeacherStatus;
  username?: string;
}

export interface TeacherRequest {
  employeeNo: string;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  designation: string;
  joiningDate: string;
}

export interface TemporaryPasswordResponse {
  username: string;
  temporaryPassword: string;
}

export interface CreatedTeacherResponse {
  teacher: Teacher;
  account: TemporaryPasswordResponse;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TeacherListParams {
  q?: string;
  status?: TeacherStatus;
  page?: number;
  size?: number;
  sort?: string;
}

export interface ClassRef {
  classId: number;
  className: string;
  academicYear: string;
}

export interface SubjectAssignment {
  classId: number;
  className: string;
  academicYear: string;
  subjectId: number;
  subjectCode: string;
  subjectName: string;
}

export interface TeachingLoad {
  classTeacherOf: ClassRef[];
  subjects: SubjectAssignment[];
}

export async function listTeachers(params: TeacherListParams = {}): Promise<PageResponse<Teacher>> {
  const { data } = await api.get<PageResponse<Teacher>>('/teachers', { params });
  return data;
}

export async function getTeacher(id: number): Promise<Teacher> {
  const { data } = await api.get<Teacher>(`/teachers/${id}`);
  return data;
}

export async function createTeacher(request: TeacherRequest): Promise<CreatedTeacherResponse> {
  const { data } = await api.post<CreatedTeacherResponse>('/teachers', request);
  return data;
}

export async function updateTeacher(id: number, request: TeacherRequest): Promise<Teacher> {
  const { data } = await api.put<Teacher>(`/teachers/${id}`, request);
  return data;
}

export async function updateTeacherStatus(id: number, status: TeacherStatus): Promise<Teacher> {
  const { data } = await api.patch<Teacher>(`/teachers/${id}/status`, { status });
  return data;
}

export async function getTeacherTeachingLoad(id: number): Promise<TeachingLoad> {
  const { data } = await api.get<TeachingLoad>(`/teachers/${id}/teaching-load`);
  return data;
}

export async function getMyTeachingLoad(): Promise<TeachingLoad> {
  const { data } = await api.get<TeachingLoad>('/teachers/me/teaching-load');
  return data;
}

export const teacherKeys = {
  all: ['teachers'] as const,
  list: (params: TeacherListParams) => ['teachers', 'list', params] as const,
  active: ['teachers', 'active'] as const,
  teachingLoad: (id: number) => ['teachers', id, 'teaching-load'] as const,
  myTeachingLoad: ['teachers', 'me', 'teaching-load'] as const,
};
