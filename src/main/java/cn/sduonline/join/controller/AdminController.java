package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.BoardCreateRequest;
import cn.sduonline.join.data.dto.BoardVO;
import cn.sduonline.join.data.dto.DepartmentCreateRequest;
import cn.sduonline.join.data.dto.DepartmentVO;
import cn.sduonline.join.data.dto.RoleAssignmentRequest;
import cn.sduonline.join.data.dto.RoleAssignmentMemberVO;
import cn.sduonline.join.data.dto.RoleAssignmentVO;
import cn.sduonline.join.data.dto.UserSearchVO;
import cn.sduonline.join.data.dto.WorkstationCreateRequest;
import cn.sduonline.join.data.dto.WorkstationVO;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.AdminOrganizationService;
import cn.sduonline.join.service.AdminRoleAssignmentService;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.security.scope.OrgType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
