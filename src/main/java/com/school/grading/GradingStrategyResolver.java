package com.school.grading;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Selects the configured {@link GradingStrategy} bean at startup and fails fast on unknown codes.
 */
@Component
public class GradingStrategyResolver {

    private final Map<String, GradingStrategy> byCode;
    private final GradingStrategy activeStrategy;

    public GradingStrategyResolver(
            List<GradingStrategy> strategies,
            @Value("${app.grading.strategy:GPA_5}") String configured
    ) {
        Map<String, GradingStrategy> map = new LinkedHashMap<>();
        for (GradingStrategy strategy : strategies) {
            map.put(strategy.code(), strategy);
        }
        this.byCode = Collections.unmodifiableMap(map);

        GradingStrategy chosen = configured == null ? null : this.byCode.get(configured.trim());
        if (chosen == null) {
            throw new IllegalStateException(
                    "Unknown grading strategy '" + configured + "'. Use one of " + this.byCode.keySet()
            );
        }
        this.activeStrategy = chosen;
    }

    public GradingStrategy active() {
        return activeStrategy;
    }

    public Map<String, GradingStrategy> all() {
        return byCode;
    }
}
