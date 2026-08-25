package cn.sduonline.join.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;

class ExternalIdentityClientTest {

    @Test
    void eagerlyInitializesFeignMessageConverters() {
        AtomicBoolean customized = new AtomicBoolean();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean(
                "clientHttpMessageConvertersCustomizer",
                (ClientHttpMessageConvertersCustomizer) builder -> customized.set(true)
        );

        ExternalIdentityClient.FeignConfiguration configuration =
                new ExternalIdentityClient.FeignConfiguration();
        var converters = configuration.feignHttpMessageConverters(
                beanFactory.getBeanProvider(ClientHttpMessageConvertersCustomizer.class),
                beanFactory.getBeanProvider(HttpMessageConverterCustomizer.class)
        );

        assertThat(customized).isTrue();
        assertThat(converters.getConverters()).isNotEmpty();
    }
}
