import { useQuery } from '@tanstack/react-query';
import { academicKeys, listSubjects } from '../api/academics';

/** Cached list of subjects (ordered by code) for tables and selectors. Shared by every screen. */
export function useSubjects() {
  return useQuery({ queryKey: academicKeys.subjects, queryFn: listSubjects, staleTime: 5 * 60_000 });
}
