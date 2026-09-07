package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.CheckInQrCodeVO;
import cn.sduonline.join.data.dto.CheckInRequest;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.DepartmentCheckInService;
import cn.sduonline.join.service.ServiceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DepartmentCheckInController {

    private final DepartmentCheckInService checkInService;

    /**
     * 生成部门动态签到二维码内容
     *
     * @param departmentId 部门 ID
     * @param sessionId 面试场次 ID
     * @return 二维码内容、过期时间和建议刷新间隔
     */
    @GetMapping("/departments/{departmentId}/interviews/sessions/"
            + "{sessionId}/check-in/qr-code")
    @DepartmentPermission(PermissionCode.CHECK_IN_MANAGE)
    public Result<CheckInQrCodeVO> createQrCode(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long sessionId
    ) {
        ServiceResult<CheckInQrCodeVO> result =
                checkInService.createQrCode(departmentId, sessionId);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 完成签到并获取叫号序号。启用二维码的场次需要提供动态令牌；
     * 未启用时需提供部门 ID 和场次 ID。
     * 场次关闭签到后，已有 RECHECK_IN_REQUIRED 记录的本人可以继续恢复排队，
     * 此时传部门 ID 和场次 ID 即可，无需二维码令牌。
     *
     * @param request 签到令牌
     * @return 签到记录和叫号序号
     */
    @PostMapping("/check-ins")
    public Result<CheckInVO> checkIn(
            @Valid @RequestBody CheckInRequest request
    ) {
        ServiceResult<CheckInVO> result = checkInService.checkIn(
                request.departmentId(), request.sessionId(), request.token(),
                StpUtil.getLoginIdAsString()
        );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 取消当前用户在指定面试场次的签到。
     */
    @DeleteMapping("/departments/{departmentId}/interviews/sessions/"
            + "{sessionId}/check-ins/me")
    public Result<Void> cancelCheckIn(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long sessionId
    ) {
        ServiceResult<Void> result = checkInService.cancelCheckIn(
                departmentId, sessionId, StpUtil.getLoginIdAsString()
        );
        return result.isSuccess()
                ? Result.ok()
                : Result.fail(result.error());
    }
}
