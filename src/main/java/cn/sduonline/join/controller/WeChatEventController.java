package cn.sduonline.join.controller;

import cn.sduonline.join.client.WeChatInboundMessage;
import cn.sduonline.join.client.WeChatSignature;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.service.WeChatBindSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对接公众号「服务器配置」：验证接入签名，接收关注等事件推送。
 * 当前只处理明文模式；未在此处理的消息一律返回空串，符合被动回复协议。
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat/mp")
@RequiredArgsConstructor
public class WeChatEventController {

    private final WeChatProperties properties;
    private final WeChatBindSessionService sessionService;

    /** 公众号后台保存服务器配置时的接入校验。 */
    @GetMapping("/callback")
    public ResponseEntity<String> verify(
            @RequestParam String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestParam String echostr
    ) {
        properties.validateServerMessage();
        if (!WeChatSignature.matches(
                properties.getServerToken(), signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }
        return ResponseEntity.ok(echostr);
    }

    /** 接收消息/事件推送。 */
    @PostMapping("/callback")
    public ResponseEntity<String> receive(
            @RequestParam String signature,
            @RequestParam String timestamp,
            @RequestParam String nonce,
            @RequestBody String body
    ) {
        properties.validateServerMessage();
        if (!WeChatSignature.matches(
                properties.getServerToken(), signature, timestamp, nonce)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }

        WeChatInboundMessage message = WeChatInboundMessage.parse(body);
        if (message.isSubscribeEvent()) {
            log.info("WeChat subscribe event received, resuming pending bind if any");
            sessionService.completeFollowUp(message.fromUserName());
        }
        return ResponseEntity.ok("");
    }
}
