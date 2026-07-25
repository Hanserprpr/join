package cn.sduonline.join.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.sduonline.join.client.WeChatApiClient;
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
            context.registerBean(
                    ObjectMapper.class,
                    () -> JsonMapper.builder().build());
            context.refresh();

            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
            assertThat(context.getBean(WeChatApiClient.class)).isNotNull();
        }
    }
}
