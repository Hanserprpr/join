package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DepartmentDetailUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentDetailPatchRequest;
import cn.sduonline.join.data.dto.DepartmentVO;
import cn.sduonline.join.data.dto.DepartmentQuestionnaireUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentQuestionnaireVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.DepartmentService;
import cn.sduonline.join.service.DepartmentQuestionnaireService;
import cn.sduonline.join.service.ServiceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 部门接口
 */
@Validated
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentQuestionnaireService questionnaireService;

    /**
     * 获取部门详情
     *
     * @param departmentId 部门 ID
     * @return 部门详情
     */
    @GetMapping("/{departmentId}")
    public Result<DepartmentVO> getDepartment(
            @PathVariable @Positive Long departmentId
    ) {
        String casId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : null;
        return toResult(departmentService.findById(departmentId, casId));
    }

    /**
     * 完整更新部门详情
     *
     * @param departmentId 部门 ID
     * @param request 完整更新参数
     * @return 更新后的部门详情
     * @apiNote 不支持部分更新，未传字段会按空值覆盖，需要部分更新时使用 PATCH 接口
     */
    @PutMapping("/{departmentId}")
    @DepartmentPermission(PermissionCode.RECRUITMENT_MANAGE)
    public Result<DepartmentVO> updateDepartment(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody DepartmentDetailUpdateRequest request
    ) {
        return toResult(departmentService.updateDetail(
                departmentId, StpUtil.getLoginIdAsString(), request
        ));
    }

    /**
     * 部分更新部门详情
     *
     * @param departmentId 部门 ID
     * @param request 部分更新参数
     * @return 更新后的部门详情
     * @apiNote 支持部分更新，未传字段保持原值，显式传 null 会清空对应字段
     */
    @PatchMapping("/{departmentId}")
    @DepartmentPermission(PermissionCode.RECRUITMENT_MANAGE)
    public Result<DepartmentVO> patchDepartment(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody DepartmentDetailPatchRequest request
    ) {
        return toResult(departmentService.patchDetail(
                departmentId, StpUtil.getLoginIdAsString(), request
        ));
    }

    /**
     * 获取部门报名问卷
     *
     * @param departmentId 部门 ID
     * @return 部门报名问卷，未设置时返回空题目列表
     */
    @SaCheckLogin
    @GetMapping("/{departmentId}/questionnaire")
    public Result<DepartmentQuestionnaireVO> getQuestionnaire(
            @PathVariable @Positive Long departmentId
    ) {
        return toQuestionnaireResult(
                questionnaireService.findByDepartmentId(departmentId)
        );
    }

    /**
     * 完整更新部门报名问卷
     *
     * @param departmentId 部门 ID
     * @param request 问卷题目配置
     * @return 更新后的部门报名问卷
     * @apiNote 不支持部分更新，请求中的题目列表会整体替换原问卷
     */
    @PutMapping("/{departmentId}/questionnaire")
    @DepartmentPermission(PermissionCode.RECRUITMENT_MANAGE)
    public Result<DepartmentQuestionnaireVO> updateQuestionnaire(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody DepartmentQuestionnaireUpdateRequest request
    ) {
        return toQuestionnaireResult(
                questionnaireService.replace(departmentId, request)
        );
    }

    /**
     * 清空部门报名问卷
     *
     * @param departmentId 部门 ID
     * @return 清空后的部门报名问卷
     */
    @DeleteMapping("/{departmentId}/questionnaire")
    @DepartmentPermission(PermissionCode.RECRUITMENT_MANAGE)
    public Result<DepartmentQuestionnaireVO> clearQuestionnaire(
            @PathVariable @Positive Long departmentId
    ) {
        return toQuestionnaireResult(questionnaireService.clear(departmentId));
    }

    private static Result<DepartmentVO> toResult(ServiceResult<DepartmentVO> result) {
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    private static Result<DepartmentQuestionnaireVO> toQuestionnaireResult(
            ServiceResult<DepartmentQuestionnaireVO> result
    ) {
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
