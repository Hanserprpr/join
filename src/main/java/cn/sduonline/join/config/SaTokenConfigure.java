package cn.sduonline.join.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    // 注册 Sa-Token 拦截器，打开注解式鉴权功能
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器，打开注解式鉴权功能
        // 使用 SaAsyncAwareInterceptor 跳过 ASYNC 等异步重派发时的重复鉴权
        registry.addInterceptor(new SaAsyncAwareInterceptor()).addPathPatterns("/**");
    }
}
