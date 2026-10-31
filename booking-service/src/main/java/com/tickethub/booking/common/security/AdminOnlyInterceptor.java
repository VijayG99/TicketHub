package com.tickethub.booking.common.security;

import com.tickethub.booking.common.exception.ForbiddenException;
import com.tickethub.booking.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class AdminOnlyInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod method = (HandlerMethod) handler;
        boolean adminOnly = method.hasMethodAnnotation(AdminOnly.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), AdminOnly.class);
        if (!adminOnly) {
            return true;
        }
        CurrentUser user = CurrentUser.from(request);
        if (!user.isAuthenticated()) {
            throw new UnauthorizedException("Authentication is required");
        }
        if (!user.isAdmin()) {
            throw new ForbiddenException("This operation requires the ADMIN role");
        }
        return true;
    }
}
