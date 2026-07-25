package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.client.WeChatApiException;
import cn.sduonline.join.config.WeChatProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WeChatAccessTokenService {

    private final WeChatApiClient apiClient;
    private final WeChatProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public WeChatAccessTokenService(
            WeChatApiClient apiClient,
            WeChatProperties properties) {
        this(apiClient, properties, Clock.systemUTC());
    }

    WeChatAccessTokenService(
            WeChatApiClient apiClient,
            WeChatProperties properties,
            Clock clock) {
        this.apiClient = apiClient;
        this.properties = properties;
        this.clock = clock;
    }

    public String getAccessToken() {
        CachedToken current = cachedToken;
        if (isUsable(current)) {
            return current.value();
        }
        return refresh(false);
    }

    public String forceRefresh() {
        return refresh(true);
    }

    private synchronized String refresh(boolean forceRefresh) {
        if (!forceRefresh && isUsable(cachedToken)) {
            return cachedToken.value();
        }

        properties.validate();
        WeChatApiClient.StableTokenResponse response = apiClient.getStableToken(
                properties.getAppId(), properties.getAppSecret(), forceRefresh);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            Integer code = response == null ? null : response.errcode();
            String message = response == null ? "微信接口返回空响应" : response.errmsg();
            throw new WeChatApiException(code, "获取微信 access_token 失败：" + message);
        }

        long expiresIn = response.expiresIn() == null ? 7200 : response.expiresIn();
        long refreshAhead = Math.max(0, properties.getTokenRefreshAheadSeconds());
        long cacheSeconds = Math.max(1, expiresIn - Math.min(refreshAhead, expiresIn - 1));
        cachedToken = new CachedToken(
                response.accessToken(), clock.instant().plusSeconds(cacheSeconds));
        return response.accessToken();
    }

    private boolean isUsable(CachedToken token) {
        return token != null && clock.instant().isBefore(token.refreshAt());
    }

    private record CachedToken(String value, Instant refreshAt) {
    }
}
