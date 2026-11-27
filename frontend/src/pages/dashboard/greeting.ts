import { SCHOOL_TIME_ZONE } from '../../utils/schoolDate';

/** "morning" before 12:00, "afternoon" before 17:00, else "evening", by the school's clock in Dhaka. */
export function greeting(now: Date = new Date()): string {
  const hour = Number(
    new Intl.DateTimeFormat('en-GB', { timeZone: SCHOOL_TIME_ZONE, hour: '2-digit', hourCycle: 'h23' }).format(now),
  );
  if (hour < 12) return 'morning';
  if (hour < 17) return 'afternoon';
  return 'evening';
}

/** "Nasima Akter" -> "Nasima". */
export function firstName(fullName: string | undefined): string {
  return fullName?.trim().split(/\s+/)[0] ?? '';
}
