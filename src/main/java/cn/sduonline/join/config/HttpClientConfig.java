package cn.sduonline.join.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 应用 HTTP 客户端配置。
 *
 * <p>部分 Spring Boot WebMVC 组合不会自动提供 {@link RestClient.Builder}，
 * 因此在缺少该 Bean 时显式创建，供微信等外部 API 客户端注入使用。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    @ConditionalOnMissingBean(RestClient.Builder.class)
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
