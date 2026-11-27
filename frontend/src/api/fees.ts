import { api } from './client';
import type { PageResponse } from './audit';

// ---------- Fee invoices (F21) ----------

export type InvoiceStatus = 'UNPAID' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED';

export const INVOICE_STATUSES: InvoiceStatus[] = ['UNPAID', 'PARTIALLY_PAID', 'PAID', 'CANCELLED'];

export interface Invoice {
  id: number;
  invoiceNo: string;
  studentId: number;
  studentName: string;
  admissionNo: string;
  className?: string | null;
  title: string;
  amount: number;
  discount: number;
  payable: number;
  amountPaid: number;
  outstanding: number;
  dueDate: string;
  status: InvoiceStatus;
  overdue: boolean;
}

export interface InvoiceRequest {
  studentId: number;
  title: string;
  amount: number;
  discount?: number;
  /** "YYYY-MM-DD" */
  dueDate: string;
}

export interface BulkInvoiceRequest {
  classId: number;
  title: string;
  amount: number;
  /** "YYYY-MM-DD" */
  dueDate: string;
}

export interface BulkResult {
  created: number;
  skipped: number;
}

export interface FeeSummary {
  totalBilled: number;
  totalCollected: number;
  totalOutstanding: number;
  overdueInvoices: number;
  collectionRate: number;
}

export interface InvoiceSearch {
  q?: string;
  status?: InvoiceStatus;
  classId?: number;
  overdue?: boolean;
  page?: number;
  size?: number;
}

export async function listInvoices(search: InvoiceSearch = {}): Promise<PageResponse<Invoice>> {
  const params = {
    q: search.q?.trim() || undefined,
    status: search.status,
    classId: search.classId,
    overdue: search.overdue ? true : undefined,
    page: search.page ?? 0,
    size: search.size ?? 20,
  };
  const { data } = await api.get<PageResponse<Invoice>>('/fees/invoices', { params });
  return data;
}

export async function getInvoice(id: number): Promise<Invoice> {
  const { data } = await api.get<Invoice>(`/fees/invoices/${id}`);
  return data;
}

export async function listStudentInvoices(studentId: number): Promise<Invoice[]> {
  const { data } = await api.get<Invoice[]>(`/fees/students/${studentId}/invoices`);
  return data;
}

export async function createInvoice(request: InvoiceRequest): Promise<Invoice> {
  const { data } = await api.post<Invoice>('/fees/invoices', request);
  return data;
}

export async function bulkCreateInvoices(request: BulkInvoiceRequest): Promise<BulkResult> {
  const { data } = await api.post<BulkResult>('/fees/invoices/bulk', request);
  return data;
}

export async function cancelInvoice(id: number): Promise<Invoice> {
  const { data } = await api.post<Invoice>(`/fees/invoices/${id}/cancel`);
  return data;
}

export async function getFeeSummary(): Promise<FeeSummary> {
  const { data } = await api.get<FeeSummary>('/fees/summary');
  return data;
}

export const feeKeys = {
  all: ['fees'] as const,
  list: (search: InvoiceSearch) => ['fees', 'invoices', search] as const,
  student: (studentId: number) => ['fees', 'student', studentId] as const,
  summary: ['fees', 'summary'] as const,
};
