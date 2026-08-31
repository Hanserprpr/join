package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.po.User;
import java.time.LocalDateTime;

/**
 * 平台用户管理列表中的单个用户。
 * <p>
 * 只暴露管理页面需要展示的字段；微信 OpenID 与 OIDC subject 属于身份凭据，
 * 一律不出现在列表里，只以 {@code wechatBound} 标记是否已绑定。
 *
 * @param casId 统一认证账号（学号/工号）
 * @param name 姓名
 * @param email 邮箱
 * @param phone 手机号
 * @param qq QQ 号
 * @param college 学院
 * @param major 专业
 * @param grade 入学年级
 * @param profileCompleted 报名必填资料是否已完成
 * @param wechatBound 是否已绑定微信公众号
 * @param avatarUrl 头像可访问 URL，未上传时为 null
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record AdminUserVO(
        String casId,
        String name,
        String email,
        String phone,
        String qq,
        String college,
        String major,
        Integer grade,
        boolean profileCompleted,
        boolean wechatBound,
        String avatarUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 由实体及解析后的头像 URL 转换为视图对象。
     */
    public static AdminUserVO from(User user, String avatarUrl) {
        return new AdminUserVO(
                user.getCasId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getQq(),
                user.getCollege(),
                user.getMajor(),
                user.getGrade(),
                Boolean.TRUE.equals(user.getProfileCompleted()),
                user.getWechatOpenid() != null
                        && !user.getWechatOpenid().isBlank(),
                avatarUrl,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
