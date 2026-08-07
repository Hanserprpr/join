package cn.sduonline.join.data.dto;

import java.util.List;

public record InterviewRoomStateVO(
        Long roomId,
        String roomName,
        String roomStatus,
        DepartmentInterviewVO currentInterview,
        List<InterviewRoomMemberStatusVO> administrators,
        int submittedCount,
        int administratorCount,
        List<InterviewRoomMemberStatusVO> pendingAdministrators,
        boolean currentUserSubmitted,
        boolean canForce
) {
    public boolean allSubmitted() {
        return currentInterview != null
                && administratorCount > 0
                && submittedCount == administratorCount;
    }
}
