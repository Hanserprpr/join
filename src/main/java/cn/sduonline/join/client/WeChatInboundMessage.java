package cn.sduonline.join.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** 公众号服务器配置推送的明文消息/事件，仅解析当前用到的字段。 */
public record WeChatInboundMessage(
        String toUserName,
        String fromUserName,
        String msgType,
        String event) {

    public boolean isSubscribeEvent() {
        return "event".equalsIgnoreCase(msgType) && "subscribe".equalsIgnoreCase(event);
    }

    public static WeChatInboundMessage parse(String xml) {
        if (!StringUtils.hasText(xml)) {
            throw new WeChatApiException("微信推送消息为空");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Element root = document.getDocumentElement();
            return new WeChatInboundMessage(
                    text(root, "ToUserName"),
                    text(root, "FromUserName"),
                    text(root, "MsgType"),
                    text(root, "Event"));
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new WeChatApiException("微信推送消息解析失败");
        }
    }

    private static String text(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        return nodes.item(0).getTextContent();
    }
}
