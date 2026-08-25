package cn.sduonline.join.client;

import cn.sduonline.join.data.dto.ExternalIdentityResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.http.converter.autoconfigure.ClientHttpMessageConvertersCustomizer;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 可信系统的身份接口。
 */
@FeignClient(
        name = "external-identity",
        url = "${app.external-identity.base-url}",
        configuration = ExternalIdentityClient.FeignConfiguration.class
)
public interface ExternalIdentityClient {

    @GetMapping("/isduapi/v2/api/auth/user/info")
    ExternalIdentityResponse getCurrentStudent(
            @RequestHeader("token") String token
    );

    /**
     * OpenFeign 5.0.x 延迟初始化消息转换器时存在并发竞争：一个线程刚创建空列表，
     * 其他线程就可能读到它。在 Feign 客户端子容器创建 Bean 时主动完成初始化，
     * 避免首次并发请求触发 {@code 'messageConverters' must not be empty}。
     */
    class FeignConfiguration {

        @Bean
        FeignHttpMessageConverters feignHttpMessageConverters(
                ObjectProvider<ClientHttpMessageConvertersCustomizer> customizers,
                ObjectProvider<HttpMessageConverterCustomizer> cloudCustomizers
        ) {
            FeignHttpMessageConverters converters =
                    new FeignHttpMessageConverters(customizers, cloudCustomizers);
            converters.getConverters();
            return converters;
        }
    }
}
