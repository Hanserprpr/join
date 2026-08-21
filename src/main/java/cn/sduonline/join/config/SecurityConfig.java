package cn.sduonline.join.config;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.security.OidcLoginSuccessHandler;
import cn.sduonline.join.security.RestAuthenticationEntryPoint;
import cn.sduonline.join.security.ExternalTokenAuthenticationFilter;
import cn.sduonline.join.security.SecurityErrorResponseWriter;
import cn.sduonline.join.data.vo.Result;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置：OIDC 登录、接口鉴权与 CORS。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppProperties appProperties;
    private final OidcLoginSuccessHandler oidcLoginSuccessHandler;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final ExternalTokenAuthenticationFilter externalTokenAuthenticationFilter;

    /**
     * 此 Filter 只加入 Spring Security 链，避免被 Servlet 容器再次自动注册。
     */
    @Bean
    public FilterRegistrationBean<ExternalTokenAuthenticationFilter>
            externalTokenFilterRegistration() {
        FilterRegistrationBean<ExternalTokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(externalTokenAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Spring Security 仅承载 OIDC、CORS 和外部 Token Filter。
     * 业务接口的登录及权限校验统一交给 Sa-Token 注解。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http){
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .anyRequest().permitAll()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .oauth2Login(oauth2 -> oauth2
                        // 反代只把 /recruit/api/ 转发到后端，OAuth2 端点也必须落在 /api 前缀下
                        .authorizationEndpoint(endpoint ->
                                endpoint.baseUri("/api/oauth2/authorization"))
                        .redirectionEndpoint(endpoint ->
                                endpoint.baseUri("/api/login/oauth2/code/*"))
                        .successHandler(oidcLoginSuccessHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            StpUtil.logout();
                            SecurityErrorResponseWriter.write(
                                    response,
                                    HttpServletResponse.SC_OK,
                                    Result.ok()
                            );
                        })
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JOINSESSION")
                )
                .addFilterBefore(
                        externalTokenAuthenticationFilter,
                        AnonymousAuthenticationFilter.class
                );

        return http.build();
    }

    /**
     * 跨域配置，允许前端携带 Cookie 访问。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.getCorsAllowedOriginList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
