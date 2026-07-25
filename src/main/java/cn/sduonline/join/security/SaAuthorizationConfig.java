package cn.sduonline.join.security;

import cn.dev33.satoken.stp.StpInterface;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.service.AuthorizationService;
import cn.sduonline.join.service.UserService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 角色及权限数据源。
 */
@Component
@RequiredArgsConstructor
public class SaAuthorizationConfig implements StpInterface {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String casId = String.valueOf(loginId);
        User user = userService.findByCasId(casId).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<String> roles = authorizationService.findRoles(casId);
        if (roles.contains("SYSTEM_ADMIN")) {
            return List.of("*");
        }
        return authorizationService.findPermissions(casId)
                .stream()
                .distinct()
                .toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String casId = String.valueOf(loginId);
        User user = userService.findByCasId(casId).orElse(null);
        if (user == null) {
            return List.of();
        }

        List<String> roles = new ArrayList<>(authorizationService.findRoles(casId));
        roles.add("USER");
        return roles.stream().distinct().toList();
    }
}
