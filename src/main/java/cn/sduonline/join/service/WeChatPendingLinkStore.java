package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.CheckInQrGrant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 微信授权成功但 OpenID 尚未绑定时，把待绑定信息暂存在会话里，
 * 由统一认证登录成功后取出，完成绑定并续做原动作。
 */
@Component
public class WeChatPendingLinkStore {

    private static final String SESSION_ATTRIBUTE =
            "join:wechat:pending-link";
    private static final String SEPARATOR = "|";

    public void save(HttpServletRequest request, PendingLink link) {
        if (link == null || !StringUtils.hasText(link.openid())) {
            throw new IllegalArgumentException("待绑定微信信息不完整");
        }
        request.getSession(true).setAttribute(SESSION_ATTRIBUTE, encode(link));
    }

    /** 取出并清除暂存信息，确保一次统一认证登录只消费一次。 */
    public PendingLink take(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        session.removeAttribute(SESSION_ATTRIBUTE);
        return value instanceof String text ? decode(text) : null;
    }

    /** 会话可能被容器序列化，这里只存字符串，避免依赖对象序列化。 */
    private String encode(PendingLink link) {
        CheckInQrGrant grant = link.checkInGrant();
        return grant == null
                ? link.openid()
                : link.openid() + SEPARATOR + grant.departmentId()
                        + SEPARATOR + grant.sessionId();
    }

    private PendingLink decode(String text) {
        String[] parts = text.split("\\" + SEPARATOR);
        if (parts.length == 1) {
            return new PendingLink(parts[0], null);
        }
        if (parts.length != 3) {
            return null;
        }
        try {
            return new PendingLink(parts[0], new CheckInQrGrant(
                    Long.valueOf(parts[1]), Long.valueOf(parts[2])));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record PendingLink(String openid, CheckInQrGrant checkInGrant) {
    }
}
