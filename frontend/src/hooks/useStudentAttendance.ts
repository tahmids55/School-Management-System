import { useQuery } from '@tanstack/react-query';
import { attendanceKeys, getStudentAttendance } from '../api/attendance';
import { schoolToday } from '../utils/schoolDate';

/** 1 January of the school's current year up to today, both as "YYYY-MM-DD" (Dhaka time). */
export function thisSchoolYearRange(): { year: number; from: string; to: string } {
  const to = schoolToday();
  const year = Number(to.slice(0, 4));
  return { year, from: `${year}-01-01`, to };
}

/**
 * One student's attendance for this year. The profile tab, the profile stat cards and /my/attendance
 * share the same query key, so the request is made once and cached.
 */
export function useStudentAttendanceThisYear(studentId: number | null | undefined) {
  const { from, to } = thisSchoolYearRange();
  return useQuery({
    queryKey: attendanceKeys.student(studentId ?? 0, from, to),
    queryFn: () => getStudentAttendance(studentId!, from, to),
    enabled: studentId !== null && studentId !== undefined,
  });
}
