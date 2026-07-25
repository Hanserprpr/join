package cn.sduonline.join.service;

import static org.assertj.core.api.Assertions.assertThat;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.config.WeChatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class WeChatAccessTokenServiceContextTest {

    @Test
    void springSelectsDependencyInjectionConstructor() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.registerBean(WeChatApiClient.class, () ->
                    org.mockito.Mockito.mock(WeChatApiClient.class));
            context.registerBean(WeChatProperties.class, WeChatProperties::new);
            context.register(WeChatAccessTokenService.class);
            context.refresh();

            assertThat(context.getBean(WeChatAccessTokenService.class))
                    .isNotNull();
        }
    }
}
