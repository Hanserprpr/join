package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewQueueStatus;
import java.util.List;

public record MyInterviewQueueStatusVO(
        Long departmentId,
        Long sessionId,
        Integer queueNumber,
        InterviewQueueStatus status,
        int peopleAhead,
        List<Integer> interviewingQueueNumbers,
        @io.swagger.v3.oas.annotations.media.Schema(
                description = "前方人员序号和打码姓名，按排队顺序排列")
        List<InterviewQueueAheadCandidateVO> peopleAheadCandidates
) implements SessionScopedSnapshot {
}
