package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DepartmentInterviewVO;
import cn.sduonline.join.data.dto.InterviewEvaluationRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationVO;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.InterviewRoomRequest;
import cn.sduonline.join.data.dto.InterviewRoomStateVO;
import cn.sduonline.join.data.dto.InterviewRoomVO;
import cn.sduonline.join.data.dto.sse.InterviewRoomSseEnvelope;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.DepartmentInterviewRoomService;
import cn.sduonline.join.service.DepartmentInterviewService;
import cn.sduonline.join.service.InterviewSseService;
import cn.sduonline.join.service.ServiceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/departments/{departmentId}/interview-rooms")
@RequiredArgsConstructor
public class DepartmentInterviewRoomController {

    private final DepartmentInterviewRoomService roomService;
    private final DepartmentInterviewService interviewService;
    private final InterviewSseService interviewSseService;

    @PostMapping
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewRoomVO> create(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody InterviewRoomRequest request
    ) {
        return result(roomService.create(
                departmentId, StpUtil.getLoginIdAsString(), request
        ));
    }

    @GetMapping
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<List<InterviewRoomVO>> findAll(
            @PathVariable @Positive Long departmentId
    ) {
        return result(roomService.findAll(departmentId));
    }

    @PostMapping("/{roomId}/join")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomVO> join(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(roomService.join(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/leave")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomVO> leave(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(roomService.leave(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/close")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewRoomVO> close(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(roomService.close(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/call-next")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomStateVO> callNext(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.callNextInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/force-call-next")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomStateVO> forceCallNext(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.forceCallNextInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @GetMapping("/{roomId}/current")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<DepartmentInterviewVO> current(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.findCurrentInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/current/finish")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomStateVO> finish(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.finishInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/current/force-finish")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomStateVO> forceFinish(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.forceFinishInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/current/evaluation")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomStateVO> submitEvaluation(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId,
            @Valid @RequestBody InterviewEvaluationRequest request
    ) {
        return result(interviewService.submitEvaluationInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString(), request
        ));
    }

    @GetMapping("/{roomId}/state")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewRoomStateVO> state(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.findRoomState(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @GetMapping("/{roomId}/interviews/{interviewId}/evaluations")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<List<InterviewEvaluationVO>> evaluations(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId,
            @PathVariable @Positive Long interviewId
    ) {
        return result(interviewService.findEvaluations(
                departmentId, roomId, interviewId,
                StpUtil.getLoginIdAsString()
        ));
    }

    @GetMapping(
            value = "/{roomId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @Operation(
            summary = "订阅面试房间状态事件",
            description = "interview-room-state-updated 的 data 是房间状态；"
                    + "business-error 的 data 是统一错误响应；"
                    + "heartbeat 的 data 是 ISO-8601 时间字符串。"
    )
    @ApiResponse(
            responseCode = "200",
            description = "房间状态、业务错误及心跳事件流",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(
                            implementation = InterviewRoomSseEnvelope.class
                    )
            )
    )
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public SseEmitter events(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        String casId = StpUtil.getLoginIdAsString();
        ServiceResult<InterviewRoomStateVO> initial =
                interviewService.findRoomState(departmentId, roomId, casId);
        if (!initial.isSuccess()) {
            return interviewSseService.failed(
                    Result.fail(initial.error(), initial.data())
            );
        }
        return interviewSseService.subscribeRoom(
                departmentId,
                roomId,
                "interview-room-state-updated",
                () -> interviewService
                        .findRoomState(departmentId, roomId, casId)
                        .data()
        );
    }

    @PostMapping("/{roomId}/current/pass")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewQueueItemVO> pass(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.passCurrentInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    @PostMapping("/{roomId}/current/stop-calling")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewQueueItemVO> stopCalling(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long roomId
    ) {
        return result(interviewService.stopCallingInRoom(
                departmentId, roomId, StpUtil.getLoginIdAsString()
        ));
    }

    private static <T> Result<T> result(ServiceResult<T> result) {
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error(), result.data());
    }
}
