package cn.sduonline.join.data.dto;

/** 队列推送的部门和场次范围，仅用于服务内部。 */
public record InterviewQueueScope(Long departmentId, Long sessionId) {
}
