package cn.sduonline.join.service;

import cn.sduonline.join.client.WeChatApiClient;
import cn.sduonline.join.client.WeChatApiClient.JsApiTicketResponse;
import cn.sduonline.join.client.WeChatApiException;
import cn.sduonline.join.config.WeChatProperties;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class WeChatJsApiTicketService {

    private static final int INVALID_ACCESS_TOKEN = 40014;
    private static final int EXPIRED_ACCESS_TOKEN = 42001;

    private final WeChatApiClient apiClient;
    private final WeChatAccessTokenService tokenService;
    private final WeChatProperties properties;
    private final Clock clock;
    private volatile CachedTicket cachedTicket;

    @Autowired
    public WeChatJsApiTicketService(
            WeChatApiClient apiClient,
            WeChatAccessTokenService tokenService,
            WeChatProperties properties) {
        this(apiClient, tokenService, properties, Clock.systemUTC());
    }

    WeChatJsApiTicketService(
            WeChatApiClient apiClient,
            WeChatAccessTokenService tokenService,
            WeChatProperties properties,
            Clock clock) {
        this.apiClient = apiClient;
        this.tokenService = tokenService;
        this.properties = properties;
        this.clock = clock;
    }

    public String getTicket() {
        CachedTicket current = cachedTicket;
        if (isUsable(current)) {
            return current.value();
        }
        return refresh();
    }

    private synchronized String refresh() {
        if (isUsable(cachedTicket)) {
            return cachedTicket.value();
        }

        JsApiTicketResponse response = apiClient.getJsApiTicket(
                tokenService.getAccessToken());
        if (hasTokenError(response)) {
            response = apiClient.getJsApiTicket(tokenService.forceRefresh());
        }
        if (response == null || response.ticket() == null
                || response.ticket().isBlank()) {
            Integer code = response == null ? null : response.errcode();
            String message = response == null ? "微信接口返回空响应" : response.errmsg();
            throw new WeChatApiException(
                    code, "获取微信 jsapi_ticket 失败：" + message);
        }

        long expiresIn = response.expiresIn() == null ? 7200 : response.expiresIn();
        long refreshAhead = Math.max(0, properties.getTicketRefreshAheadSeconds());
        long cacheSeconds = Math.max(
                1, expiresIn - Math.min(refreshAhead, expiresIn - 1));
        cachedTicket = new CachedTicket(
                response.ticket(), clock.instant().plusSeconds(cacheSeconds));
        return response.ticket();
    }

    private boolean hasTokenError(JsApiTicketResponse response) {
        return response != null
                && response.errcode() != null
                && (response.errcode() == INVALID_ACCESS_TOKEN
                || response.errcode() == EXPIRED_ACCESS_TOKEN);
    }

    private boolean isUsable(CachedTicket ticket) {
        return ticket != null && clock.instant().isBefore(ticket.refreshAt());
    }

    private record CachedTicket(String value, Instant refreshAt) {
    }
}
