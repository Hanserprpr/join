package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.AdminUserVO;
import cn.sduonline.join.data.dto.PageVO;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 平台用户管理：按条件分页查询本地用户。
 * <p>
 * 与角色授权页的学号联想（{@code AdminRoleAssignmentService#searchUsers}）不同，
 * 这里返回全量用户列表，因此接口层限定 {@code SYSTEM_ADMIN} 才能调用。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final AvatarService avatarService;

    /**
     * 按筛选条件分页查询用户。
     * <p>
     * 空白筛选值一律归一化为 null，避免把空串当成"学院等于空"的过滤条件。
     *
     * @param keyword 关键词，模糊匹配姓名、学号、手机号、邮箱和 QQ
     * @param college 学院筛选
     * @param major 专业筛选
     * @param grade 入学年级筛选
     * @param profileCompleted 报名必填资料是否完整
     * @param wechatBound 是否已绑定微信公众号
     * @param sortBy 排序字段，支持 createdAt、casId 和 grade
     * @param sortOrder 排序方向，支持 asc 和 desc
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 分页用户列表
     */
    @Transactional(readOnly = true)
    public ServiceResult<PageVO<AdminUserVO>> findUsers(
            String keyword,
            String college,
            String major,
            Integer grade,
            Boolean profileCompleted,
            Boolean wechatBound,
            String sortBy,
            String sortOrder,
            int page,
            int size
    ) {
        String normalizedKeyword = escapeLikeKeyword(trimToNull(keyword));
        String normalizedCollege = trimToNull(college);
        String normalizedMajor = trimToNull(major);
        long total = userMapper.countUsers(
                normalizedKeyword, normalizedCollege, normalizedMajor,
                grade, profileCompleted, wechatBound
        );
        if (total == 0) {
            return ServiceResult.success(
                    PageVO.of(List.of(), 0, page, size)
            );
        }
        List<AdminUserVO> users = userMapper.selectUsers(
                        normalizedKeyword, normalizedCollege, normalizedMajor,
                        grade, profileCompleted, wechatBound,
                        sortBy, sortOrder, (page - 1) * size, size
                )
                .stream()
                .map(user -> AdminUserVO.from(user, avatarService.publicUrl(user)))
                .toList();
        return ServiceResult.success(PageVO.of(users, total, page, size));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 转义 LIKE 通配符，避免用户输入的 % 和 _ 命中整表。
     * 转义字符在 SQL 中声明为 {@code ESCAPE '!'}。
     */
    private static String escapeLikeKeyword(String value) {
        return value == null ? null : value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
