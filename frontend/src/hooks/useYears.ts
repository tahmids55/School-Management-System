import { useQuery } from '@tanstack/react-query';
import { academicKeys, listAcademicYears } from '../api/academics';

/** Cached list of academic years (newest first) for tables and selectors. Shared by every screen. */
export function useYears() {
  const query = useQuery({ queryKey: academicKeys.years, queryFn: listAcademicYears, staleTime: 5 * 60_000 });
  return { ...query, currentYear: query.data?.find((year) => year.current) };
}
