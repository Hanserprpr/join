package cn.sduonline.join.data.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record InterviewQueueAheadCandidateVO(
        Integer queueNumber,
        @Schema(description = "姓名仅保留第一个字，其余以 * 替代")
        String candidateName
) {
}
