package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.AdminUserVO;
import cn.sduonline.join.data.dto.BoardCreateRequest;
import cn.sduonline.join.data.dto.BoardVO;
import cn.sduonline.join.data.dto.DepartmentCreateRequest;
import cn.sduonline.join.data.dto.DepartmentVO;
import cn.sduonline.join.data.dto.OrganizationNameUpdateRequest;
import cn.sduonline.join.data.dto.OrganizationNameVO;
import cn.sduonline.join.data.dto.PageVO;
import cn.sduonline.join.data.dto.RoleAssignmentRequest;
import cn.sduonline.join.data.dto.RoleAssignmentMemberVO;
import cn.sduonline.join.data.dto.RoleAssignmentVO;
import cn.sduonline.join.data.dto.UserSearchVO;
import cn.sduonline.join.data.dto.WorkstationCreateRequest;
import cn.sduonline.join.data.dto.WorkstationVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.AdminOrganizationService;
import cn.sduonline.join.service.AdminRoleAssignmentService;
import cn.sduonline.join.service.AdminUserService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.security.scope.OrgType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminOrganizationService organizationService;
    private final AdminRoleAssignmentService roleAssignmentService;
    private final AdminUserService userService;

    /**
     * 创建板块
     */
    @PostMapping("/boards")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<BoardVO> createBoard(
            @Valid @RequestBody BoardCreateRequest request
    ) {
        ServiceResult<BoardVO> result = organizationService.createBoard(request);
        return result.isSuccess() ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 创建工作站
     */
    @PostMapping("/workstations")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<WorkstationVO> createWorkstation(
            @Valid @RequestBody WorkstationCreateRequest request
    ) {
        ServiceResult<WorkstationVO> result = organizationService.createWorkstation(request);
        return result.isSuccess() ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 创建部门
     */
    @PostMapping("/departments")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<DepartmentVO> createDepartment(
            @Valid @RequestBody DepartmentCreateRequest request
    ) {
        ServiceResult<DepartmentVO> result = organizationService.createDepartment(request);
        return result.isSuccess() ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 修改板块名称。
     */
    @PatchMapping("/boards/{boardId}")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<OrganizationNameVO> updateBoardName(
            @PathVariable @Positive Long boardId,
            @Valid @RequestBody OrganizationNameUpdateRequest request
    ) {
        ServiceResult<OrganizationNameVO> result =
                organizationService.updateBoardName(boardId, request);
        return result.isSuccess()
                ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 修改工作站名称。
     */
    @PatchMapping("/workstations/{workstationId}")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<OrganizationNameVO> updateWorkstationName(
            @PathVariable @Positive Long workstationId,
            @Valid @RequestBody OrganizationNameUpdateRequest request
    ) {
        ServiceResult<OrganizationNameVO> result =
                organizationService.updateWorkstationName(
                        workstationId, request
                );
        return result.isSuccess()
                ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 修改部门名称。
     */
    @PatchMapping("/departments/{departmentId}")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<OrganizationNameVO> updateDepartmentName(
            @PathVariable @Positive Long departmentId,
            @Valid @RequestBody OrganizationNameUpdateRequest request
    ) {
        ServiceResult<OrganizationNameVO> result =
                organizationService.updateDepartmentName(
                        departmentId, request
                );
        return result.isSuccess()
                ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 删除板块(级联删除其下所有工作站、部门及关联数据)
     */
    @DeleteMapping("/boards/{boardId}")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<Void> deleteBoard(@PathVariable Long boardId) {
        ServiceResult<Void> result = organizationService.deleteBoard(boardId);
        return result.isSuccess() ? Result.ok() : Result.fail(result.error());
    }

    /**
     * 删除工作站(级联删除其下所有部门及关联数据)
     */
    @DeleteMapping("/workstations/{workstationId}")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<Void> deleteWorkstation(@PathVariable Long workstationId) {
        ServiceResult<Void> result = organizationService.deleteWorkstation(workstationId);
        return result.isSuccess() ? Result.ok() : Result.fail(result.error());
    }

    /**
     * 删除部门(级联删除报名、面试、签到等关联数据)
     */
    @DeleteMapping("/departments/{departmentId}")
    @SaCheckRole("SYSTEM_ADMIN")
    public Result<Void> deleteDepartment(@PathVariable Long departmentId) {
        ServiceResult<Void> result = organizationService.deleteDepartment(departmentId);
        return result.isSuccess() ? Result.ok() : Result.fail(result.error());
    }

    /**
     * 查询一个组织节点的有效角色授权成员。
     * 返回记录保留原始作用域，包含直接授权、上下级继承授权和 ALL 授权。
     */
    @GetMapping("/role-assignments")
    @SaCheckLogin
    public Result<List<RoleAssignmentMemberVO>> getRoleAssignments(
            @RequestParam OrgType scopeType,
            @RequestParam @Positive Long scopeId
    ) {
        ServiceResult<List<RoleAssignmentMemberVO>> result =
                roleAssignmentService.findMembers(
                        StpUtil.getLoginIdAsString(), scopeType, scopeId
                );
        return result.isSuccess() ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 分页查询平台用户，支持关键词与资料字段筛选。
     * <p>
     * 与 {@code GET /api/admin/users} 的学号联想不同，本接口可返回全量用户，
     * 因此只对平台管理员开放。
     *
     * @param keyword 关键词，模糊匹配姓名、学号、手机号、邮箱和 QQ
     * @param college 学院筛选
     * @param major 专业筛选
     * @param grade 入学年级筛选
     * @param profileCompleted 报名必填资料是否完整
     * @param wechatBound 是否已绑定微信公众号
     * @param sortBy 排序字段，支持注册时间、学号和年级
     * @param sortOrder 排序方向，支持升序和降序
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 分页用户列表
     */
    @GetMapping("/users/page")
    @SaCheckRole("SYSTEM_ADMIN")
    @Operation(description = "返回全量用户，仅平台管理员可用；"
            + "GET /api/admin/users 是角色授权页的学号联想，不分页。"
            + "列表不返回微信 OpenID 与 OIDC sub，只给 wechatBound 标记。")
    public Result<PageVO<AdminUserVO>> findUsers(
            @Parameter(description = "关键词，模糊匹配姓名、学号、手机号、邮箱和 QQ")
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 64) String college,
            @RequestParam(required = false) @Size(max = 64) String major,
            @RequestParam(required = false) @Min(2000) @Max(2100) Integer grade,
            @Parameter(description = "报名必填资料是否完整；不传则不过滤")
            @RequestParam(required = false) Boolean profileCompleted,
            @Parameter(description = "是否已绑定微信公众号；不传则不过滤")
            @RequestParam(required = false) Boolean wechatBound,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "createdAt|casId|grade") String sortBy,
            @RequestParam(defaultValue = "desc")
            @Pattern(regexp = "asc|desc") String sortOrder,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        ServiceResult<PageVO<AdminUserVO>> result = userService.findUsers(
                keyword, college, major, grade, profileCompleted, wechatBound,
                sortBy, sortOrder, page, size
        );
        return result.isSuccess()
                ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 按学号模糊联想可授予角色的本地用户；少于六位或空输入返回空列表。
     */
    @GetMapping("/users")
    @SaCheckLogin
    public Result<List<UserSearchVO>> searchUsers(
            @RequestParam(required = false, defaultValue = "")
            @Pattern(regexp = "^[A-Za-z0-9_-]{0,32}$") String casId
    ) {
        ServiceResult<List<UserSearchVO>> result = roleAssignmentService.searchUsers(
                StpUtil.getLoginIdAsString(), casId
        );
        return result.isSuccess() ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 分配角色
     */
    @PostMapping("/role-assignments")
    @SaCheckLogin
    public Result<RoleAssignmentVO> assignRole(
            @Valid @RequestBody RoleAssignmentRequest request
    ) {
        ServiceResult<RoleAssignmentVO> result = roleAssignmentService.assign(
                StpUtil.getLoginIdAsString(), request
        );
        return result.isSuccess() ? Result.ok(result.data()) : Result.fail(result.error());
    }

    /**
     * 撤销角色分配
     */
    @DeleteMapping("/role-assignments")
    @SaCheckLogin
    public Result<Void> revokeRole(
            @Valid @RequestBody RoleAssignmentRequest request
    ) {
        ServiceResult<Void> result = roleAssignmentService.revoke(
                StpUtil.getLoginIdAsString(), request
        );
        return result.isSuccess() ? Result.ok() : Result.fail(result.error());
    }
}
