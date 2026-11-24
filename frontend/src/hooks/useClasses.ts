import { useQuery } from '@tanstack/react-query';
import { classKeys, listClasses } from '../api/classes';

/**
 * Cached list of classes of one year (ordered by name, then section).
 * Without a year id: the current year's classes. Pass enabled = false to wait for a choice.
 */
export function useClasses(academicYearId?: number, enabled = true) {
  return useQuery({
    queryKey: classKeys.list(academicYearId),
    queryFn: () => listClasses(academicYearId),
    staleTime: 60_000,
    enabled,
  });
}
