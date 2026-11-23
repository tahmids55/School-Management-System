package com.school.security;

import java.util.Set;

/**
 * Extension contract returning the school class IDs a teacher username may access.
 * Defaults to an empty set (deny by default) until the teacher assignment module provides an implementation.
 */
public interface TeachingScope {

    Set<Long> accessibleClassIds(String teacherUsername);
}
