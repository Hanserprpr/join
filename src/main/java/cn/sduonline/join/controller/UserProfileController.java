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
import cn.sduonline.join.service.AvatarService;
import java.io.IOException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
    private final AvatarService avatarService;

    /**
     * 获取当前登录用户的个人资料
     *
     * @return 当前用户个人资料
     */
    @GetMapping
    public Result<UserProfileVO> profile() {
        return userService.findByCasId(StpUtil.getLoginIdAsString())
                .map(user -> UserProfileVO.from(user, avatarService.publicUrl(user)))
                .map(Result::ok)
                .orElseGet(() -> Result.fail(BizCode.USER_NOT_FOUND));
    }

    /**
     * 更新当前登录用户的个人资料
     *
     * @param request 需要更新的个人资料字段
     * @return 更新后的个人资料
     * @apiNote 支持部分更新，未传字段保持原值，至少需要提供一个可更新字段；
     *          选填字段（邮箱、QQ 号）传空字符串表示清空
     */
    @PutMapping
    public Result<UserProfileVO> updateContact(
            @Valid @RequestBody ContactUpdateRequest request
    ) {
        if (request.email() == null
                && !StringUtils.hasText(request.phone())
                && !StringUtils.hasText(request.college())
                && !StringUtils.hasText(request.major())
                && request.grade() == null
                && request.qq() == null) {
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
                ? Result.ok(UserProfileVO.from(
                        result.data(), avatarService.publicUrl(result.data())))
                : Result.fail(result.error());
    }

    /**
     * 上传或替换当前用户头像。
     *
     * @param file JPEG、PNG、GIF 或 WebP 图片，最大 2 MiB（可配置）
     * @return 包含新头像 URL 的完整个人资料
     */
    @PostMapping(value = "/avatar", consumes = "multipart/form-data")
    public Result<UserProfileVO> uploadAvatar(
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        ServiceResult<User> result = avatarService.updateAvatar(
                StpUtil.getLoginIdAsString(), file
        );
        return result.isSuccess()
                ? Result.ok(UserProfileVO.from(
                        result.data(), avatarService.publicUrl(result.data())))
                : Result.fail(result.error());
    }
}
