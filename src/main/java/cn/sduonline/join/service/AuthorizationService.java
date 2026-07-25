package cn.sduonline.join.service;

import cn.sduonline.join.mapper.AuthorizationMapper;
import cn.sduonline.join.security.scope.OrgType;
import java.util.List;
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
}
