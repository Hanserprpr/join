package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.UserPermissionsVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.AuthorizationService;
import cn.sduonline.join.service.UserService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户权限查询接口
 */
@SaCheckLogin
@RestController
@RequestMapping("/api/user/permissions")
@RequiredArgsConstructor
public class UserPermissionsController {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    /**
     * 获取当前用户的权限画像：角色、权限码及按权限可管理的部门
     *
     * @return 当前用户的权限画像
     */
    @GetMapping
    public Result<UserPermissionsVO> getMyPermissions() {
        String casId = StpUtil.getLoginIdAsString();
        User user = userService.findByCasId(casId).orElse(null);
        if (user == null) {
            return Result.fail(BizCode.NOT_LOGIN, "登录用户不存在");
        }

        List<String> roles = new ArrayList<>(authorizationService.findRoles(casId));
        roles.add("USER");
        boolean isSystemAdmin = roles.contains("SYSTEM_ADMIN");
        List<String> permissions = isSystemAdmin
                ? List.of("*")
                : authorizationService.findPermissions(casId)
                        .stream()
                        .distinct()
                        .toList();

        return Result.ok(new UserPermissionsVO(
                user.getCasId(),
                user.getName(),
                roles.stream().distinct().toList(),
                permissions,
                isSystemAdmin,
                authorizationService.findManagedDepartments(casId),
                authorizationService.findDepartmentAccess(casId)
        ));
    }
}
