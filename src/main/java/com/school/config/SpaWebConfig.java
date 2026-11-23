package com.school.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves static frontend assets and falls back to index.html for extensionless SPA routes.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());
    }

    static boolean isExcludedFromSpaFallback(String resourcePath) {
        if (resourcePath == null) {
            return true;
        }
        String normalized = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        if (normalized.contains(".")) {
            return true;
        }
        return normalized.equals("api") || normalized.startsWith("api/")
                || normalized.equals("actuator") || normalized.startsWith("actuator/")
                || normalized.equals("v3/api-docs") || normalized.startsWith("v3/api-docs/")
                || normalized.equals("swagger-ui") || normalized.startsWith("swagger-ui/");
    }

    static class SpaPathResourceResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = super.getResource(resourcePath, location);
            if (requested != null) {
                return requested;
            }
            if (isExcludedFromSpaFallback(resourcePath)) {
                return null;
            }
            Resource index = new ClassPathResource("/static/index.html");
            return index.exists() && index.isReadable() ? index : null;
        }
    }
}
