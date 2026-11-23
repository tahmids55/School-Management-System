import dayjs from 'dayjs';

type DateInput = string | Date | dayjs.Dayjs | null | undefined;

const money = new Intl.NumberFormat('en-BD', { style: 'currency', currency: 'BDT' });

/** 1500 -> "BDT 1,500.00" (the exact symbol depends on the browser's locale data). */
export function formatMoney(amount: number | string | null | undefined): string {
  if (amount === null || amount === undefined || amount === '') return '-';
  const value = Number(amount);
  return Number.isFinite(value) ? money.format(value) : '-';
}

/** "2026-11-23" -> "23 Nov 2026". */
export function formatDate(value: DateInput): string {
  if (!value) return '-';
  const date = dayjs(value);
  return date.isValid() ? date.format('DD MMM YYYY') : '-';
}

/** "2026-11-23T09:05:00Z" -> "23 Nov 2026, 15:05" in the browser's time zone. */
export function formatDateTime(value: DateInput): string {
  if (!value) return '-';
  const date = dayjs(value);
  return date.isValid() ? date.format('DD MMM YYYY, HH:mm') : '-';
}

/** 87.456 -> "87.5%". */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined || !Number.isFinite(value)) return '-';
  return `${value.toFixed(1)}%`;
}

/** Any date -> "YYYY-MM-DD", the format the API expects for LocalDate. */
export function toIsoDate(value: DateInput): string | undefined {
  if (!value) return undefined;
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD') : undefined;
}

/** "ON_LEAVE" -> "On leave". */
export function humanize(value: string | null | undefined): string {
  if (!value) return '';
  const words = value.replace(/[_-]+/g, ' ').trim().toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}
