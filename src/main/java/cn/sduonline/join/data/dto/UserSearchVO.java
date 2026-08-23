package cn.sduonline.join.data.dto;

/**
 * 角色授权时按学号联想的最小用户信息。
 *
 * @param casId 学号/统一认证账号
 * @param name 姓名
 */
public record UserSearchVO(
        String casId,
        String name
) {
}
