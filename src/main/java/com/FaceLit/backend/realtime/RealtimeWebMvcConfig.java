package com.FaceLit.backend.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RealtimeWebMvcConfig implements WebMvcConfigurer {

    private final RealtimeChangeInterceptor interceptor;

    public RealtimeWebMvcConfig(RealtimeChangeInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor);
    }
}
