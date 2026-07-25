package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DepartmentInterviewVO;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.MyInterviewQueueStatusVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigRequest;
import cn.sduonline.join.data.dto.InterviewQueueConfigPatchRequest;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.DepartmentInterviewService;
import cn.sduonline.join.service.InterviewSseService;
import cn.sduonline.join.service.ServiceResult;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
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
    private final InterviewSseService interviewSseService;

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
     * 从等待队列中叫取下一位可面试用户
     *
     * @param departmentId 部门 ID
     * @return 新创建的进行中面试记录
     */
    @PostMapping("/call-next")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<DepartmentInterviewVO> callNext(
            @PathVariable @Positive Long departmentId
    ) {
        return toResult(interviewService.callNext(
                departmentId, StpUtil.getLoginIdAsString()
        ));
    }

    /**
     * 查询当前管理员正在进行的面试
     *
     * @param departmentId 部门 ID
     * @return 当前管理员的进行中面试记录
     */
    @GetMapping("/current")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<DepartmentInterviewVO> findCurrent(
            @PathVariable @Positive Long departmentId
    ) {
        return toResult(interviewService.findCurrent(
                departmentId, StpUtil.getLoginIdAsString()
        ));
    }

    /**
     * 结束当前管理员正在进行的面试
     *
     * @param departmentId 部门 ID
     * @return 已结束的面试记录
     */
    @PostMapping("/current/finish")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<DepartmentInterviewVO> finish(
            @PathVariable @Positive Long departmentId
    ) {
        return toResult(interviewService.finish(
                departmentId, StpUtil.getLoginIdAsString()
        ));
    }

    /**
     * 将当前叫到的用户标记为过号并向后顺延
     *
     * @param departmentId 部门 ID
     * @return 重新排队后的用户状态
     */
    @PostMapping("/current/pass")
    @DepartmentPermission(PermissionCode.INTERVIEW_EVALUATE)
    public Result<InterviewQueueItemVO> passCurrent(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<InterviewQueueItemVO> result =
                interviewService.passCurrent(
                        departmentId, StpUtil.getLoginIdAsString()
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
}
