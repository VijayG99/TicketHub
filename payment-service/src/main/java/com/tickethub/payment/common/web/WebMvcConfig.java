package com.tickethub.payment.common.web;

import com.tickethub.payment.common.security.AdminOnlyInterceptor;
import com.tickethub.payment.common.security.CurrentUserArgumentResolver;
import com.tickethub.payment.common.security.CurrentUser;
import java.util.List;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    static {
        // CurrentUser is injected from gateway headers; it is not a request parameter in the API docs (ST-51)
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminOnlyInterceptor());
    }
}
