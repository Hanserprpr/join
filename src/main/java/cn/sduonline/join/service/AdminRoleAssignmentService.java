package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.RoleAssignmentRequest;
import cn.sduonline.join.data.dto.RoleAssignmentVO;
import cn.sduonline.join.data.dto.RoleAssignmentMemberVO;
import cn.sduonline.join.data.dto.UserSearchVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.UserRoleScope;
import cn.sduonline.join.mapper.AdminRoleAssignmentMapper;
import cn.sduonline.join.security.scope.OrgType;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRoleAssignmentService {

    private static final int USER_SEARCH_MIN_KEYWORD_LENGTH = 6;

    private static final Map<String, RoleRule> ASSIGNABLE_ROLES = Map.of(
            "BOARD_ADMIN", new RoleRule(OrgType.BOARD, 40),
            "WORKSTATION_ADMIN", new RoleRule(OrgType.WORKSTATION, 30),
            "DEPARTMENT_ADMIN", new RoleRule(OrgType.DEPARTMENT, 20),
            "DEPARTMENT_ASSISTANT", new RoleRule(OrgType.DEPARTMENT, 10)
    );

    private final AdminRoleAssignmentMapper assignmentMapper;

    /**
     * 查询与目标组织有关的角色授权成员。
     * <p>
     * 返回的每一条数据保留原始身份作用域；成员既可能是该节点或下级的直接授权，
     * 也可能是覆盖该节点的上级授权。平台管理员不作为成员返回。
     *
     * @param operatorCasId 当前操作人学号
     * @param scopeType 要查看的组织类型
     * @param scopeId 要查看的组织 ID
     * @return 角色授权成员列表
     */
    @Transactional(readOnly = true)
    public ServiceResult<List<RoleAssignmentMemberVO>> findMembers(
            String operatorCasId,
            OrgType scopeType,
            Long scopeId
    ) {
        if (!canManageRoleAssignments(operatorCasId)) {
            return ServiceResult.failure(BizCode.NO_PERMISSION);
        }
        if (!scopeExists(scopeType, scopeId)) {
            return ServiceResult.failure(BizCode.ORG_SCOPE_NOT_FOUND);
        }
        if (assignmentMapper.countScopeCoverage(
                operatorCasId, scopeType.name(), scopeId
        ) == 0) {
            return ServiceResult.failure(BizCode.NO_PERMISSION);
        }
        return ServiceResult.success(assignmentMapper.selectMembersByScope(
                scopeType.name(), scopeId
        ));
    }

    /**
     * 角色授权页面按学号模糊联想本地已存在的用户。
     * 少于六位或空输入不查库，避免将用户表作为全量列表返回。
     *
     * @param operatorCasId 当前操作人学号
     * @param casIdKeyword 学号关键字
     * @return 最多 20 条候选用户
     */
    @Transactional(readOnly = true)
    public ServiceResult<List<UserSearchVO>> searchUsers(
            String operatorCasId,
            String casIdKeyword
    ) {
        if (!canManageRoleAssignments(operatorCasId)) {
            return ServiceResult.failure(BizCode.NO_PERMISSION);
        }
        if (casIdKeyword == null || casIdKeyword.isBlank()) {
            return ServiceResult.success(List.of());
        }
        String keyword = casIdKeyword.strip();
        if (keyword.length() < USER_SEARCH_MIN_KEYWORD_LENGTH) {
            return ServiceResult.success(List.of());
        }
        String escapedKeyword = escapeLikeKeyword(keyword);
        return ServiceResult.success(
                assignmentMapper.selectUsersByCasIdKeyword(escapedKeyword)
        );
    }

    @Transactional
    public ServiceResult<RoleAssignmentVO> assign(
            String operatorCasId,
            RoleAssignmentRequest request
    ) {
        if (assignmentMapper.countAdminRole(operatorCasId) == 0) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_FORBIDDEN);
        }
        if (assignmentMapper.countUser(request.casId()) == 0) {
            return ServiceResult.failure(BizCode.USER_NOT_FOUND);
        }

        String roleCode = request.roleCode().trim().toUpperCase();
        RoleRule roleRule = ASSIGNABLE_ROLES.get(roleCode);
        if (roleRule == null) {
            return ServiceResult.failure(BizCode.ROLE_NOT_FOUND);
        }
        if (roleRule.scopeType() != request.scopeType()) {
            return ServiceResult.failure(BizCode.ROLE_SCOPE_MISMATCH);
        }
        if (!scopeExists(request.scopeType(), request.scopeId())) {
            return ServiceResult.failure(BizCode.ORG_SCOPE_NOT_FOUND);
        }
        if (assignmentMapper.countGrantAuthority(
                operatorCasId,
                roleRule.level(),
                request.scopeType().name(),
                request.scopeId()
        ) == 0) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_FORBIDDEN);
        }

        Long roleId = assignmentMapper.selectRoleId(roleCode);
        if (roleId == null) {
            return ServiceResult.failure(BizCode.ROLE_NOT_FOUND);
        }
        if (assignmentMapper.countAssignment(
                request.casId(), roleId, request.scopeType().name(), request.scopeId()
        ) > 0) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_EXISTS);
        }

        UserRoleScope assignment = new UserRoleScope();
        assignment.setCasId(request.casId());
        assignment.setRoleId(roleId);
        assignment.setScopeType(request.scopeType().name());
        assignment.setScopeId(request.scopeId());
        try {
            assignmentMapper.insertAssignment(assignment);
        } catch (DuplicateKeyException exception) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_EXISTS);
        }
        return ServiceResult.success(RoleAssignmentVO.from(assignment, roleCode));
    }

    /**
     * 撤销角色分配。
     * 与授予对称：仅当操作者持有高于目标角色的身份、且其数据范围覆盖目标组织时允许撤销。
     *
     * @param operatorCasId 操作者学号
     * @param request 被撤销的角色分配
     * @return 撤销结果
     */
    @Transactional
    public ServiceResult<Void> revoke(
            String operatorCasId,
            RoleAssignmentRequest request
    ) {
        if (assignmentMapper.countAdminRole(operatorCasId) == 0) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_FORBIDDEN);
        }
        String roleCode = request.roleCode().trim().toUpperCase();
        RoleRule roleRule = ASSIGNABLE_ROLES.get(roleCode);
        if (roleRule == null) {
            return ServiceResult.failure(BizCode.ROLE_NOT_FOUND);
        }
        if (roleRule.scopeType() != request.scopeType()) {
            return ServiceResult.failure(BizCode.ROLE_SCOPE_MISMATCH);
        }
        Long roleId = assignmentMapper.selectRoleId(roleCode);
        if (roleId == null) {
            return ServiceResult.failure(BizCode.ROLE_NOT_FOUND);
        }
        if (assignmentMapper.countAssignment(
                request.casId(), roleId,
                request.scopeType().name(), request.scopeId()
        ) == 0) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_NOT_FOUND);
        }
        if (assignmentMapper.countGrantAuthority(
                operatorCasId,
                roleRule.level(),
                request.scopeType().name(),
                request.scopeId()
        ) == 0) {
            return ServiceResult.failure(BizCode.ROLE_ASSIGNMENT_FORBIDDEN);
        }
        assignmentMapper.deleteAssignment(
                request.casId(), roleId,
                request.scopeType().name(), request.scopeId()
        );
        return ServiceResult.success(null);
    }

    private boolean scopeExists(OrgType scopeType, Long scopeId) {
        if (scopeId == null || scopeId <= 0) {
            return false;
        }
        return switch (scopeType) {
            case BOARD -> assignmentMapper.countEnabledBoard(scopeId) > 0;
            case WORKSTATION -> assignmentMapper.countEnabledWorkstation(scopeId) > 0;
            case DEPARTMENT -> assignmentMapper.countEnabledDepartment(scopeId) > 0;
        };
    }

    private boolean canManageRoleAssignments(String casId) {
        return assignmentMapper.countRoleAssignmentManager(casId) > 0;
    }

    /**
     * 使用 ! 作为 LIKE 转义字符，避免学号中的下划线被当作单字符通配符。
     */
    private static String escapeLikeKeyword(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private record RoleRule(OrgType scopeType, int level) {
    }
}
