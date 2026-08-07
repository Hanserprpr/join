package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DepartmentInterviewVO;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.MyInterviewQueueStatusVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigRequest;
import cn.sduonline.join.data.dto.InterviewQueueConfigPatchRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationVO;
import cn.sduonline.join.data.dto.InterviewSessionRequest;
import cn.sduonline.join.data.dto.InterviewSessionVO;
import cn.sduonline.join.data.dto.sse.InterviewQueueSseEnvelope;
import cn.sduonline.join.data.dto.sse.MyQueueStatusSseEnvelope;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.DepartmentInterviewService;
import cn.sduonline.join.service.DepartmentInterviewSessionService;
import cn.sduonline.join.service.InterviewSseService;
import cn.sduonline.join.service.ServiceResult;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/departments/{departmentId}/interviews")
@RequiredArgsConstructor
public class DepartmentInterviewController {

    private final DepartmentInterviewService interviewService;
    private final DepartmentInterviewSessionService sessionService;
    private final InterviewSseService interviewSseService;

    /**
     * 创建面试场次草稿
     *
     * @param departmentId 部门 ID
     * @param request 面试时间、地点和取号上限
     * @return 创建后的面试场次
     */
    @PostMapping("/sessions")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewSessionVO> createSession(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody InterviewSessionRequest request
    ) {
        return toSessionResult(sessionService.create(departmentId, request));
    }

    /**
     * 修改尚未结束的面试场次
     *
     * @param departmentId 部门 ID
     * @param sessionId 面试场次 ID
     * @param request 面试时间、地点和取号上限
     * @return 修改后的面试场次
     */
    @PutMapping("/sessions/{sessionId}")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewSessionVO> updateSession(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long sessionId,
            @Valid @RequestBody InterviewSessionRequest request
    ) {
        return toSessionResult(sessionService.update(
                departmentId, sessionId, request
        ));
    }

    /**
     * 发布面试场次并绑定待使用的顺延资格
     *
     * @param departmentId 部门 ID
     * @param sessionId 面试场次 ID
     * @return 已发布的面试场次
     */
    @PostMapping("/sessions/{sessionId}/publish")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewSessionVO> publishSession(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long sessionId
    ) {
        return toSessionResult(sessionService.publish(
                departmentId, sessionId
        ));
    }

    /**
     * 结束面试场次并为未叫到用户生成顺延资格
     *
     * @param departmentId 部门 ID
     * @param sessionId 面试场次 ID
     * @return 已结束的面试场次
     * @apiNote 存在进行中的面试时不能结束场次
     */
    @PostMapping("/sessions/{sessionId}/end")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewSessionVO> endSession(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long sessionId
    ) {
        return toSessionResult(sessionService.end(departmentId, sessionId));
    }

    /**
     * 查询当前已发布的面试场次
     *
     * @param departmentId 部门 ID
     * @return 当前场次的时间、地点和取号上限
     */
    @GetMapping("/sessions/current")
    public Result<InterviewSessionVO> findPublishedSession(
            @PathVariable @Positive Long departmentId
    ) {
        return toSessionResult(sessionService.findPublished(departmentId));
    }

    /**
     * 查询部门全部面试场次
     *
     * @param departmentId 部门 ID
     * @return 按开始时间倒序排列的面试场次
     */
    @GetMapping("/sessions")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<List<InterviewSessionVO>> findSessions(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<List<InterviewSessionVO>> result =
                sessionService.findAll(departmentId);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 查询部门过号配置
     *
     * @param departmentId 部门 ID
     * @return 过号顺延位数和最大过号次数
     */
    @GetMapping("/queue-config")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewQueueConfigVO> findQueueConfig(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<InterviewQueueConfigVO> result =
                interviewService.findQueueConfig(departmentId);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 修改部门过号配置
     *
     * @param departmentId 部门 ID
     * @param request 过号配置
     * @return 更新后的过号配置
     * @apiNote 不支持部分更新，必须同时提供顺延位数和最大过号次数
     */
    @PutMapping("/queue-config")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewQueueConfigVO> updateQueueConfig(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody InterviewQueueConfigRequest request
    ) {
        ServiceResult<InterviewQueueConfigVO> result =
                interviewService.updateQueueConfig(departmentId, request);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 部分修改部门过号配置
     *
     * @param departmentId 部门 ID
     * @param request 需要修改的过号配置字段
     * @return 更新后的过号配置
     * @apiNote 支持部分更新，未传字段或显式传 null 的字段保持原值
     */
    @PatchMapping("/queue-config")
    @DepartmentPermission(PermissionCode.INTERVIEW_MANAGE)
    public Result<InterviewQueueConfigVO> patchQueueConfig(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody InterviewQueueConfigPatchRequest request
    ) {
        ServiceResult<InterviewQueueConfigVO> result =
                interviewService.patchQueueConfig(departmentId, request);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 查询部门完整面试队列
     *
     * @param departmentId 部门 ID
     * @return 按叫号序号排列的面试队列
     */
    @GetMapping("/queue")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<List<InterviewQueueItemVO>> findQueue(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<List<InterviewQueueItemVO>> result =
                interviewService.findQueue(departmentId);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 查询当前用户在部门中的排队状态
     *
     * @param departmentId 部门 ID
     * @return 当前用户的号码、前方人数和面试状态
     */
    @GetMapping("/my-queue-status")
    public Result<MyInterviewQueueStatusVO> findMyQueueStatus(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<MyInterviewQueueStatusVO> result =
                interviewService.findMyQueueStatus(
                        departmentId, StpUtil.getLoginIdAsString()
                );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 通过 SSE 订阅部门面试队列实时事件
     *
     * @param departmentId 部门 ID
     * @return 部门队列 SSE 连接
     * @apiNote SSE 接口，推送 queue-updated 和 heartbeat 事件
     */
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "订阅面试队列事件",
            description = "queue-updated 的 data 是面试队列快照；"
                    + "heartbeat 的 data 是 ISO-8601 时间字符串。"
    )
    @ApiResponse(
            responseCode = "200",
            description = "队列快照及心跳事件流",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(
                            implementation = InterviewQueueSseEnvelope.class
                    )
            )
    )
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public SseEmitter subscribeQueue(
            @PathVariable @Positive Long departmentId
    ) {
        return interviewSseService.subscribe(
                departmentId,
                "queue-updated",
                () -> interviewService.findQueue(departmentId).data()
        );
    }

    /**
     * 通过 SSE 订阅当前用户排队状态实时事件
     *
     * @param departmentId 部门 ID
     * @return 个人排队状态 SSE 连接
     * @apiNote SSE 接口，推送 my-queue-status-updated、business-error 和 heartbeat 事件
     */
    @GetMapping(
            value = "/my-events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @Operation(
            summary = "订阅我的排队状态事件",
            description = "my-queue-status-updated 的 data 是个人排队状态；"
                    + "business-error 的 data 是统一错误响应；"
                    + "heartbeat 的 data 是 ISO-8601 时间字符串。"
    )
    @ApiResponse(
            responseCode = "200",
            description = "个人排队状态、业务错误及心跳事件流",
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(
                            implementation = MyQueueStatusSseEnvelope.class
                    )
            )
    )
    public SseEmitter subscribeMyQueueStatus(
            @PathVariable @Positive Long departmentId
    ) {
        String casId = StpUtil.getLoginIdAsString();
        ServiceResult<MyInterviewQueueStatusVO> initial =
                interviewService.findMyQueueStatus(departmentId, casId);
        if (!initial.isSuccess()) {
            return interviewSseService.failed(Result.fail(initial.error()));
        }
        return interviewSseService.subscribe(
                departmentId,
                "my-queue-status-updated",
                () -> interviewService
                        .findMyQueueStatus(departmentId, casId)
                        .data()
        );
    }

    /**
     * 查询指定面试记录及其评分、评价
     *
     * @param departmentId 部门 ID
     * @param interviewId 面试记录 ID
     * @return 指定面试记录
     */
    @GetMapping("/{interviewId}")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<DepartmentInterviewVO> findById(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long interviewId
    ) {
        return toResult(interviewService.findById(
                departmentId, interviewId
        ));
    }

    /**
     * 补写或修改当前用户对指定面试的评价。
     * 一个面试有多位面试官各自提交的评价，本接口只更新当前用户的那一份。
     *
     * @param departmentId 部门 ID
     * @param interviewId 面试记录 ID
     * @param request 评分和评价
     * @return 当前用户对该面试的评价
     */
    @PutMapping("/{interviewId}/evaluation")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewEvaluationVO> updateEvaluation(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long interviewId,
            @Valid @RequestBody InterviewEvaluationRequest request
    ) {
        ServiceResult<InterviewEvaluationVO> result =
                interviewService.updateEvaluation(
                        departmentId, interviewId,
                        StpUtil.getLoginIdAsString(), request
                );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    private static Result<DepartmentInterviewVO> toResult(
            ServiceResult<DepartmentInterviewVO> result
    ) {
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    private static Result<InterviewSessionVO> toSessionResult(
            ServiceResult<InterviewSessionVO> result
    ) {
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
