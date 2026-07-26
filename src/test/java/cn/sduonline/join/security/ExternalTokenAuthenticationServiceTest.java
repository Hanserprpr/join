package cn.sduonline.join.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.exception.NotLoginException;
import cn.sduonline.join.client.ExternalIdentityClient;
import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.ExternalIdentityResponse;
import cn.sduonline.join.service.UserService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class ExternalTokenAuthenticationServiceTest {

    @Test
    void invalidIdentityDoesNotStoreRawTokenInException() {
        String rawToken = "sensitive-raw-token";
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        ExternalIdentityClient identityClient = mock(ExternalIdentityClient.class);

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Map.of());
        when(identityClient.getCurrentStudent(rawToken)).thenReturn(
                new ExternalIdentityResponse(
                        0,
                        "ok",
                        new ExternalIdentityResponse.ExternalUser(
                                "20240001", null, "计算机学院", "计算机科学"
                        )
                )
        );

        ExternalTokenAuthenticationService service =
                new ExternalTokenAuthenticationService(
                        redisTemplate,
                        identityClient,
                        new AppProperties(),
                        mock(UserService.class)
                );

        assertThatThrownBy(() -> service.authenticate(rawToken))
                .isInstanceOfSatisfying(NotLoginException.class, exception -> {
                    assertThat(exception.getLoginType()).isEqualTo("external");
                    assertThat(exception.getType()).isEqualTo(NotLoginException.INVALID_TOKEN);
                    assertThat(exception.getMessage()).doesNotContain(rawToken);
                });
    }

    @Test
    void missingTokenUsesNotTokenType() {
        ExternalTokenAuthenticationService service =
                new ExternalTokenAuthenticationService(
                        mock(StringRedisTemplate.class),
                        mock(ExternalIdentityClient.class),
                        new AppProperties(),
                        mock(UserService.class)
                );

        assertThatThrownBy(() -> service.authenticate(null))
                .isInstanceOfSatisfying(NotLoginException.class, exception -> {
                    assertThat(exception.getLoginType()).isEqualTo("external");
                    assertThat(exception.getType()).isEqualTo(NotLoginException.NOT_TOKEN);
                });
    }
}
