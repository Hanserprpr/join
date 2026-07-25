package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.DepartmentApplicationRequest;
import cn.sduonline.join.data.dto.DepartmentApplicationVO;
import cn.sduonline.join.data.dto.DepartmentApplicationDetailVO;
import cn.sduonline.join.data.dto.DepartmentApplicationSummaryVO;
import cn.sduonline.join.data.dto.PageVO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
     * @param grade 年级筛选
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
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ServiceResult<PageVO<DepartmentApplicationSummaryVO>> result =
                applicationService.findApplications(
                        departmentId, keyword, college, grade, page, size
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
     * 按筛选条件导出部门报名信息 Excel
     *
     * @param departmentId 部门 ID
     * @param keyword 搜索关键词，可匹配姓名、学号、手机号和 QQ
     * @param college 学院筛选
     * @param grade 年级筛选
     * @return Excel 文件或业务错误
     */
    @GetMapping("/{departmentId}/applications/export")
    @DepartmentPermission(PermissionCode.APPLICATION_EXPORT)
    public ResponseEntity<?> exportApplications(
            @PathVariable @Positive Long departmentId,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String college,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer grade
    ) {
        ServiceResult<java.util.List<DepartmentApplicationDetailVO>> result =
                applicationService.findForExport(
                        departmentId, keyword, college, grade
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
     * 提交部门报名及问卷答案
     *
     * @param departmentId 部门 ID
     * @param request 问卷答案，无问卷时可为空
     * @return 创建后的报名记录
     */
    @PostMapping("/{departmentId}/applications")
    public Result<DepartmentApplicationVO> submit(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody(required = false) DepartmentApplicationRequest request
    ) {
        DepartmentApplicationRequest safeRequest = request == null
                ? new DepartmentApplicationRequest(null)
                : request;
        ServiceResult<DepartmentApplicationVO> result = applicationService.submit(
                departmentId, StpUtil.getLoginIdAsString(), safeRequest
        );
        return result.isSuccess()
                ? Result.ok(result.data())
                : Result.fail(result.error());
    }
}
