package cn.sduonline.join.controller;

import cn.sduonline.join.data.enums.Campus;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DepartmentApplicationRequest;
import cn.sduonline.join.data.dto.AdmissionPublishRequest;
import cn.sduonline.join.data.dto.AdmissionPublishVO;
import cn.sduonline.join.data.dto.DepartmentApplicationVO;
import cn.sduonline.join.data.dto.MyDepartmentApplicationVO;
import cn.sduonline.join.data.dto.DepartmentApplicationDetailVO;
import cn.sduonline.join.data.dto.DepartmentApplicationSummaryVO;
import cn.sduonline.join.data.dto.PageVO;
import cn.sduonline.join.data.enums.ApplicationStatus;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.DepartmentPermission;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.DepartmentApplicationService;
import cn.sduonline.join.service.ApplicationExcelExportService;
import cn.sduonline.join.service.ServiceResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;

@Validated
@SaCheckLogin
@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentApplicationController {

    private final DepartmentApplicationService applicationService;
    private final ApplicationExcelExportService excelExportService;

    /**
     * 分页查询部门报名信息
     *
     * @param departmentId 部门 ID
     * @param keyword 搜索关键词，可匹配姓名、学号、手机号和 QQ
     * @param college 学院筛选
     * @param campus 学生校区筛选，不传则包含所有校区及未填写的用户
     * @param grade 年级筛选
     * @param interviewed 是否已完成面试
     * @param status 报名状态筛选，不传则查询全部状态
     * @param sortBy 排序字段，支持报名时间和评分
     * @param sortOrder 排序方向，支持升序和降序
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 分页报名信息
     */
    @GetMapping("/{departmentId}/applications")
    @DepartmentPermission(PermissionCode.APPLICATION_READ)
    public Result<PageVO<DepartmentApplicationSummaryVO>> findApplications(
            @PathVariable @Positive Long departmentId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String college,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer grade,
            @RequestParam(required = false) Boolean interviewed,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "submittedAt")
            @Pattern(regexp = "submittedAt|score") String sortBy,
            @RequestParam(defaultValue = "desc")
            @Pattern(regexp = "asc|desc") String sortOrder,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Campus campus
    ) {
        ServiceResult<PageVO<DepartmentApplicationSummaryVO>> result =
                applicationService.findApplications(
                        departmentId, keyword, college, grade, interviewed,
                        status, sortBy, sortOrder, page, size,
                        campus
                );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 获取部门内指定报名记录详情
     *
     * @param departmentId 部门 ID
     * @param applicationId 报名记录 ID
     * @return 报名人资料及问卷答案
     */
    @GetMapping("/{departmentId}/applications/{applicationId}")
    @DepartmentPermission(PermissionCode.APPLICATION_READ)
    public Result<DepartmentApplicationDetailVO> getApplicationDetail(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long applicationId
    ) {
        ServiceResult<DepartmentApplicationDetailVO> result =
                applicationService.findApplicationDetail(
                        departmentId, applicationId
                );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 将指定报名人设置为录取
     *
     * @param departmentId 部门 ID
     * @param applicationId 报名记录 ID
     * @return 更新后的报名状态
     */
    @PutMapping("/{departmentId}/applications/{applicationId}/admission")
    @DepartmentPermission(PermissionCode.ADMISSION_MANAGE)
    public Result<DepartmentApplicationVO> admit(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long applicationId
    ) {
        ServiceResult<DepartmentApplicationVO> result =
                applicationService.admit(departmentId, applicationId);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    @DeleteMapping("/{departmentId}/applications/{applicationId}/admission")
    @DepartmentPermission(PermissionCode.ADMISSION_MANAGE)
    public Result<DepartmentApplicationVO> cancelAdmissionDraft(
            @PathVariable @Positive Long departmentId,
            @PathVariable @Positive Long applicationId
    ) {
        ServiceResult<DepartmentApplicationVO> result =
                applicationService.cancelAdmissionDraft(
                        departmentId, applicationId
                );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    @PostMapping("/{departmentId}/applications/admissions/publish")
    @DepartmentPermission(PermissionCode.ADMISSION_MANAGE)
    public Result<AdmissionPublishVO> publishAdmissions(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody AdmissionPublishRequest request
    ) {
        ServiceResult<AdmissionPublishVO> result =
                applicationService.publishAdmissions(departmentId, request);
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    @GetMapping("/{departmentId}/applications/me")
    public Result<MyDepartmentApplicationVO> findMyApplication(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<MyDepartmentApplicationVO> result =
                applicationService.findMyApplication(
                        departmentId, StpUtil.getLoginIdAsString()
                );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }

    /**
     * 取消当前用户在指定部门的报名。
     */
    @DeleteMapping("/{departmentId}/applications/me")
    public Result<Void> cancelMyApplication(
            @PathVariable @Positive Long departmentId
    ) {
        ServiceResult<Void> result = applicationService.cancelMyApplication(
                departmentId, StpUtil.getLoginIdAsString()
        );
        return result.isSuccess()
                ? Result.ok()
                : Result.fail(result.error());
    }

    /**
     * 按筛选条件导出部门报名信息 Excel
     *
     * @param departmentId 部门 ID
     * @param keyword 搜索关键词，可匹配姓名、学号、手机号和 QQ
     * @param college 学院筛选
     * @param campus 学生校区筛选，不传则不限制校区
     * @param grade 年级筛选
     * @param interviewed 是否已完成面试
     * @return Excel 文件或业务错误
     */
    @GetMapping("/{departmentId}/applications/export")
    @DepartmentPermission(PermissionCode.APPLICATION_EXPORT)
    public ResponseEntity<?> exportApplications(
            @PathVariable @Positive Long departmentId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String college,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer grade,
            @RequestParam(required = false) Boolean interviewed,
            @RequestParam(required = false) Campus campus
    ) {
        ServiceResult<java.util.List<DepartmentApplicationDetailVO>> result =
                applicationService.findForExport(
                        departmentId, keyword, college, grade, interviewed,
                        campus
                );
        if (!result.isSuccess()) {
            return ResponseEntity.badRequest().body(Result.fail(result.error()));
        }
        byte[] content = excelExportService.export(result.data());
        String filename = "department-" + departmentId + "-applications-"
                + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\""
                )
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                ))
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 提交部门报名及问卷答案。
     * <p>
     * 部门未配置报名问卷时不允许报名。
     *
     * @param departmentId 部门 ID
     * @param request 问卷答案，问卷全部为选填时可为空
     * @return 创建后的报名记录
     */
    @PostMapping("/{departmentId}/applications")
    public Result<MyDepartmentApplicationVO> submit(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody(required = false) DepartmentApplicationRequest request
    ) {
        DepartmentApplicationRequest safeRequest = request == null
                ? new DepartmentApplicationRequest(null)
                : request;
        ServiceResult<MyDepartmentApplicationVO> result = applicationService.submit(
                departmentId, StpUtil.getLoginIdAsString(), safeRequest
        );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
