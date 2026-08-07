package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.RoleAssignmentRequest;
import cn.sduonline.join.data.dto.RoleAssignmentVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.UserRoleScope;
import cn.sduonline.join.mapper.AdminRoleAssignmentMapper;
import cn.sduonline.join.security.scope.OrgType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRoleAssignmentService {

    private static final Map<String, RoleRule> ASSIGNABLE_ROLES = Map.of(
            "BOARD_ADMIN", new RoleRule(OrgType.BOARD, 40),
            "WORKSTATION_ADMIN", new RoleRule(OrgType.WORKSTATION, 30),
            "DEPARTMENT_ADMIN", new RoleRule(OrgType.DEPARTMENT, 20),
            "DEPARTMENT_ASSISTANT", new RoleRule(OrgType.DEPARTMENT, 10)
    );

    private final AdminRoleAssignmentMapper assignmentMapper;

    @Transactional
    public ServiceResult<RoleAssignmentVO> assign(
            String operatorCasId,
            RoleAssignmentRequest request
    ) {
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

    private record RoleRule(OrgType scopeType, int level) {
    }
}
