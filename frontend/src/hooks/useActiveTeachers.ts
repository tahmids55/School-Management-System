import { useQuery } from '@tanstack/react-query';
import { listTeachers, teacherKeys } from '../api/teachers';

/** Cached list of active teachers (ordered by first name) for class and subject selectors. */
export function useActiveTeachers(enabled = true) {
  return useQuery({
    queryKey: teacherKeys.active,
    queryFn: async () => (await listTeachers({ status: 'ACTIVE', size: 200 })).content,
    staleTime: 60_000,
    enabled,
  });
}
