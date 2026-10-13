package com.intelliblog.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Protects every /api/** route except login/register and the public reading endpoints. */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final JwtUtil jwt;

    public WebConfig(JwtUtil jwt) { this.jwt = jwt; }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        try { Files.createDirectories(Path.of("uploads")); } catch (Exception ignored) { }
        registry.addResourceHandler("/uploads/**").addResourceLocations(Path.of("uploads").toAbsolutePath().toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                String h = req.getHeader("Authorization");
                if (h != null && h.startsWith("Bearer ")) {
                    Long id = jwt.userId(h.substring(7));
                    if (id != null) { req.setAttribute("userId", id); return true; }
                }
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"Please log in\"}");
                return false;
            }
        }).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**", "/api/public/**");
    }
}
