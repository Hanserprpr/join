package cn.sduonline.join.client;

import cn.sduonline.join.data.dto.ExternalIdentityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * 可信系统的身份接口。
 */
@FeignClient(
        name = "external-identity",
        url = "${app.external-identity.base-url}"
)
public interface ExternalIdentityClient {

    @GetMapping("/isduapi/v2/api/auth/user/info")
    ExternalIdentityResponse getCurrentStudent(
            @RequestHeader("token") String token
    );
}
