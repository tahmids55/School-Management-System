export const SCHOOL_TIME_ZONE = 'Asia/Dhaka';

/**
 * Today's date at the school, as "YYYY-MM-DD", whatever time zone the browser is in.
 * At 01:00 in Dhaka a laptop set to London still shows yesterday; the school day is what counts.
 * The 'en-CA' locale formats dates as YYYY-MM-DD.
 */
export function schoolToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: SCHOOL_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(now);
}
