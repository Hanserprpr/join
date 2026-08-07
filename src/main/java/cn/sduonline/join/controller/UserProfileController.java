package cn.sduonline.join.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.ContactUpdateRequest;
import cn.sduonline.join.data.dto.UserProfileVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.service.ServiceResult;
import cn.sduonline.join.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@SaCheckLogin
@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserService userService;

    /**
     * 获取当前登录用户的个人资料
     *
     * @return 当前用户个人资料
     */
    @GetMapping
    public Result<UserProfileVO> profile() {
        return userService.findByCasId(StpUtil.getLoginIdAsString())
                .map(UserProfileVO::from)
                .map(Result::ok)
                .orElseGet(() -> Result.fail(BizCode.USER_NOT_FOUND));
    }

    /**
     * 更新当前登录用户的个人资料
     *
     * @param request 需要更新的个人资料字段
     * @return 更新后的个人资料
     * @apiNote 支持部分更新，未传字段保持原值，至少需要提供一个可更新字段
     */
    @PutMapping
    public Result<UserProfileVO> updateContact(
            @Valid @RequestBody ContactUpdateRequest request
    ) {
        if (!StringUtils.hasText(request.email())
                && !StringUtils.hasText(request.phone())
                && !StringUtils.hasText(request.college())
                && !StringUtils.hasText(request.major())
                && request.grade() == null
                && !StringUtils.hasText(request.qq())) {
            return Result.fail(
                    BizCode.PARAM_INVALID,
                    "至少填写一个可更新的个人资料字段"
            );
        }
        ServiceResult<User> result = userService.updateContact(
                StpUtil.getLoginIdAsString(),
                request
        );
        return result.isSuccess()
                ? Result.ok(UserProfileVO.from(result.data()))
                : Result.fail(result.error());
    }
}
