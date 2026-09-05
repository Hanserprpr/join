package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.User;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 用户资料视图对象，用于接口返回。
 */
@Data
@Builder
public class UserProfileVO {

    /** OIDC subject。 */
    private String sub;

    /** 姓名。 */
    private String name;

    /** 统一认证账号（学号/工号）。 */
    private String casId;

    /** 邮箱。 */
    private String email;

    /** 手机号。 */
    private String phone;

    /** 头像的可访问 URL；未上传时为 null。 */
    private String avatarUrl;

    /** 是否已绑定微信公众号；不向前端暴露 OpenID。 */
    private boolean wechatBound;

    /** 必填资料是否已完成。 */
    private boolean profileCompleted;

    /** QQ 号。 */
    private String qq;

    /** 学院。 */
    private String college;

    /** 学生所在校区；历史资料允许为空。 */
    private Campus campus;

    /** 专业。 */
    private String major;

    /** 入学年级。 */
    private Integer grade;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;

    /**
     * 由实体转换为视图对象。
     */
    public static UserProfileVO from(User user) {
        return from(user, null);
    }

    /**
     * 由实体及解析后的头像 URL 转换为视图对象。
     */
    public static UserProfileVO from(User user, String avatarUrl) {
        if (user == null) {
            return null;
        }
        return UserProfileVO.builder()
                .sub(user.getSub())
                .name(user.getName())
                .casId(user.getCasId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(avatarUrl)
                .wechatBound(user.getWechatOpenid() != null
                        && !user.getWechatOpenid().isBlank())
                .profileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()))
                .qq(user.getQq())
                .college(user.getCollege())
                .campus(user.getCampus())
                .major(user.getMajor())
                .grade(user.getGrade())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
