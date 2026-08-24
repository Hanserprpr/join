package cn.sduonline.join.config;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
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

    /** 仅供微信 API 使用的 JDK HTTP 客户端。 */
    @Bean("wechatHttpClient")
    public HttpClient wechatHttpClient(WeChatProperties properties) {
        properties.validateProxy();
        WeChatProperties.Proxy proxy = properties.getProxy();
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(proxy.getConnectTimeoutMs()));
        if (proxy.isEnabled()) {
            builder.proxy(ProxySelector.of(new InetSocketAddress(
                    proxy.getHost(), proxy.getPort()
            )));
        }
        return builder.build();
    }

    /**
     * 微信专用 RestClient，避免代理设置污染其他外部服务客户端。
     */
    @Bean("wechatRestClientBuilder")
    public RestClient.Builder wechatRestClientBuilder(
            @Qualifier("wechatHttpClient") HttpClient httpClient,
            WeChatProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(
                properties.getProxy().getReadTimeoutMs()
        ));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
