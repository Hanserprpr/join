package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DepartmentAccessVO;
import cn.sduonline.join.data.dto.DepartmentIdentityVO;
import cn.sduonline.join.data.dto.DepartmentRoleAccess;
import cn.sduonline.join.data.dto.PermissionDepartmentAccess;
import cn.sduonline.join.mapper.AuthorizationMapper;
import cn.sduonline.join.security.scope.OrgType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final AuthorizationMapper authorizationMapper;

    public List<String> findRoles(String casId) {
        return authorizationMapper.selectRoleCodes(casId);
    }

    public List<String> findPermissions(String casId) {
        return authorizationMapper.selectPermissionCodes(casId);
    }

    /**
     * 校验某条角色授权是否同时提供指定权限并覆盖目标组织，防止把不同角色的
     * Permission 与 Scope 拼接使用。
     */
    public boolean canAccessWithPermission(
            String casId,
            String permission,
            OrgType type,
            Long targetId
    ) {
        if (targetId == null || permission == null || permission.isBlank()) {
            return false;
        }
        return switch (type) {
            case BOARD -> authorizationMapper.countBoardPermissionAccess(
                    casId, permission, targetId
            ) > 0;
            case WORKSTATION -> authorizationMapper.countWorkstationPermissionAccess(
                    casId, permission, targetId
            ) > 0;
            case DEPARTMENT -> authorizationMapper.countDepartmentPermissionAccess(
                    casId, permission, targetId
            ) > 0;
        };
    }

    /**
     * 查询该用户按权限分组可管理的启用部门 ID 列表，
     * 作用域按 ALL → BOARD → WORKSTATION → DEPARTMENT 逐级展开。
     * 平台管理员（SYSTEM_ADMIN）无具体权限关联，统一映射为 {@code *} 权限。
     *
     * @param casId 学号
     * @return 权限编码 → 可管理的启用部门 ID 列表
     */
    public Map<String, List<Long>> findManagedDepartments(String casId) {
        return authorizationMapper.selectScopedDepartmentAccess(casId)
                .stream()
                .collect(Collectors.groupingBy(
                        PermissionDepartmentAccess::permissionCode,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                PermissionDepartmentAccess::departmentId,
                                Collectors.toList()
                        )
                ));
    }

    /**
     * 查询该用户在各部门担任的身份及权限。
     * 作用域按 ALL → BOARD → WORKSTATION → DEPARTMENT 逐级展开到部门，
     * 同一部门内按角色聚合权限，平台管理员（SYSTEM_ADMIN）权限映射为 {@code *}。
     *
     * @param casId 学号
     * @return 部门访问信息列表
     */
    public List<DepartmentAccessVO> findDepartmentAccess(String casId) {
        Map<Long, DepartmentAccumulator> byDepartment = new LinkedHashMap<>();
        for (DepartmentRoleAccess row : authorizationMapper.selectDepartmentRoleAccess(casId)) {
            DepartmentAccumulator accumulator = byDepartment.computeIfAbsent(
                    row.departmentId(),
                    id -> new DepartmentAccumulator(
                            row.departmentName(), new LinkedHashMap<>()
                    )
            );
            accumulator.identities()
                    .computeIfAbsent(
                            new RoleScopeKey(
                                    row.roleCode(), row.roleName(), row.scopeType()
                            ),
                            key -> new ArrayList<>()
                    )
                    .add(row.permissionCode());
        }
        return byDepartment.entrySet().stream()
                .map(entry -> new DepartmentAccessVO(
                        entry.getKey(),
                        entry.getValue().departmentName(),
                        entry.getValue().identities().entrySet().stream()
                                .map(identity -> new DepartmentIdentityVO(
                                        identity.getKey().roleCode(),
                                        identity.getKey().roleName(),
                                        identity.getKey().scopeType(),
                                        List.copyOf(identity.getValue())
                                ))
                                .toList()
                ))
                .toList();
    }

    private record RoleScopeKey(String roleCode, String roleName, String scopeType) {}

    private record DepartmentAccumulator(
            String departmentName,
            LinkedHashMap<RoleScopeKey, List<String>> identities
    ) {}
}
