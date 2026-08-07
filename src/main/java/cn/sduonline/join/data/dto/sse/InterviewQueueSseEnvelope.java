package cn.sduonline.join.data.dto.sse;

import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "面试队列 SSE 封装结构")
public record InterviewQueueSseEnvelope(
        @Schema(
                description = "SSE 事件名",
                allowableValues = {"queue-updated", "heartbeat"}
        )
        String event,
        @Schema(
                description = "SSE data 字段；queue-updated 时为 JSON 数组",
                type = "string",
                contentMediaType = "application/json",
                contentSchema = InterviewQueueItemVO[].class
        )
        String data,
        @Schema(description = "SSE 事件 ID")
        String id,
        @Schema(description = "客户端重连等待时间（毫秒）", minimum = "0")
        Integer retry
) {
}
