package com.school.grading;

import java.math.BigDecimal;

public record GradeBand(
        int minPercent,
        String letter,
        BigDecimal point
) {
}
