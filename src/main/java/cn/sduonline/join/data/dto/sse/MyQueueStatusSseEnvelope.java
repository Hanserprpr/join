package cn.sduonline.join.data.dto.sse;

import cn.sduonline.join.data.dto.MyInterviewQueueStatusVO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "个人排队状态 SSE 封装结构")
public record MyQueueStatusSseEnvelope(
        @Schema(
                description = "SSE 事件名",
                allowableValues = {
                        "my-queue-status-updated",
                        "business-error",
                        "heartbeat"
                }
        )
        String event,
        @Schema(
                description = "SSE data 字段；状态更新时为 JSON 对象",
                type = "string",
                contentMediaType = "application/json",
                contentSchema = MyInterviewQueueStatusVO.class
        )
        String data,
        @Schema(description = "SSE 事件 ID")
        String id,
        @Schema(description = "客户端重连等待时间（毫秒）", minimum = "0")
        Integer retry
) {
}
