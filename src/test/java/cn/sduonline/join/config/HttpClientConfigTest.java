package cn.sduonline.join.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.sduonline.join.client.WeChatApiClient;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class HttpClientConfigTest {

    @Test
    void providesRestClientBuilderAndCreatesWeChatClient() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.register(HttpClientConfig.class, WeChatApiClient.class);
            context.registerBean(WeChatProperties.class, () -> {
                WeChatProperties properties = new WeChatProperties();
                properties.getProxy().setEnabled(true);
                properties.getProxy().setHost("127.0.0.1");
                properties.getProxy().setPort(8080);
                return properties;
            });
            context.registerBean(
                    ObjectMapper.class,
                    () -> JsonMapper.builder().build());
            context.refresh();

            assertThat(context.getBean(
                    "wechatRestClientBuilder", RestClient.Builder.class)).isNotNull();
            assertThat(context.getBean(WeChatApiClient.class)).isNotNull();

            HttpClient httpClient = context.getBean(
                    "wechatHttpClient", HttpClient.class);
            java.net.Proxy proxy = httpClient.proxy()
                    .orElseThrow()
                    .select(URI.create("https://api.weixin.qq.com"))
                    .getFirst();
            assertThat(proxy.address()).isEqualTo(
                    new InetSocketAddress("127.0.0.1", 8080));
        }
    }
}
