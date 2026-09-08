package cn.sduonline.join.data.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record InterviewRoomStateVO(
        Long roomId,
        Long sessionId,
        String roomName,
        String roomStatus,
        DepartmentInterviewVO currentInterview,
        List<InterviewRoomMemberStatusVO> administrators,
        int submittedCount,
        int administratorCount,
        List<InterviewRoomMemberStatusVO> pendingAdministrators,
        boolean currentUserSubmitted,
        boolean canForce
) implements SessionScopedSnapshot {
    @JsonProperty(value = "allSubmitted", access = JsonProperty.Access.READ_ONLY)
    public boolean allSubmitted() {
        return currentInterview != null
                && administratorCount > 0
                && submittedCount == administratorCount;
    }
}
