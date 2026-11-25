import { useAuth } from '../auth/AuthContext';
import { useClasses } from './useClasses';

/**
 * Classes visible to the signed-in user for the chosen (or current) academic year:
 * all classes for ADMIN, and only classes taught or led for TEACHER.
 */
export function useMyClasses(academicYearId?: number, enabled = true) {
  const { hasRole } = useAuth();
  const canReadClasses = hasRole('ADMIN', 'TEACHER');
  return useClasses(academicYearId, enabled && canReadClasses);
}
