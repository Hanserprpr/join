package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.data.dto.ContactUpdateRequest;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 本地用户持久化。
 * <p>
 * OIDC 字段映射严格对齐山大 pass-api 发现端点 {@code claims_supported}：
 * {@code sub}、{@code name}、{@code casID}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /**
     * 按 OIDC {@code sub} 查询本地用户。
     */
    public Optional<User> findBySub(String sub) {
        if (!StringUtils.hasText(sub)) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getSub, sub)
        ));
    }

    /**
     * 按学号查询本地用户。
     */
    public Optional<User> findByCasId(String casId) {
        if (!StringUtils.hasText(casId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectById(casId));
    }

    /**
     * 根据 OIDC claims 创建本地用户；学号已存在时不修改数据库。
     * <p>
     * 身份字段来自 IdP：{@code sub}、{@code name}、{@code casID}。
     * 资料字段（邮箱、手机、学院等）不由 IdP 提供，留给用户后续补全。
     */
    @Transactional
    public User syncFromOidc(OidcUser oidcUser) {
        Map<String, Object> claims = oidcUser.getClaims();

        // 发现端点 claims_supported：sub、name、casID、nonce
        String sub = claimRequired(claims, "sub");
        String name = claimRequired(claims, "name");
        String casId = claimRequired(claims, "casID");

        LocalDateTime now = LocalDateTime.now();
        User existing = userMapper.selectById(casId);
        if (existing != null) {
            return existing;
        }

        User created = new User();
        created.setSub(sub);
        created.setName(name);
        created.setCasId(casId);
            created.setEmail(null);
            created.setPhone(null);
            created.setProfileCompleted(false);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        userMapper.insert(created);
        log.info("Created local user from OIDC, sub={}, casId={}", sub, casId);
        return created;
    }

    /**
     * 根据可信外部系统身份按学号创建用户；已存在时不修改数据库。
     */
    @Transactional
    public User syncFromExternal(ExternalStudentIdentity identity) {
        String casId = identity.studentNumber().trim();
        LocalDateTime now = LocalDateTime.now();
        User existing = userMapper.selectById(casId);

        if (existing != null) {
            return existing;
        }

        User created = new User();
        created.setCasId(casId);
        created.setName(identity.name().trim());
        created.setCollege(identity.college().trim());
        created.setMajor(identity.major().trim());
        created.setEmail(null);
        created.setPhone(null);
        created.setProfileCompleted(false);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        userMapper.insert(created);
        log.info("Created local user from external identity, casId={}", casId);
        return created;
    }

    /**
     * 更新请求中提供的个人资料
     * 未提供的字段保持不变
     *
     * @param casId 当前用户统一认证账号
     * @param request 个人资料更新请求
     * @return 更新后的用户，不存在时返回空
     */
    @Transactional
    public Optional<User> updateContact(
            String casId,
            ContactUpdateRequest request
    ) {
        User user = userMapper.selectById(casId);
        if (user == null) {
            return Optional.empty();
        }
        if (StringUtils.hasText(request.email())) {
            user.setEmail(request.email().trim().toLowerCase());
        }
        if (StringUtils.hasText(request.phone())) {
            user.setPhone(request.phone().trim());
        }
        if (StringUtils.hasText(request.college())) {
            user.setCollege(request.college().trim());
        }
        if (StringUtils.hasText(request.major())) {
            user.setMajor(request.major().trim());
        }
        if (request.grade() != null) {
            user.setGrade(request.grade());
        }
        if (StringUtils.hasText(request.qq())) {
            user.setQq(request.qq().trim());
        }
        user.setProfileCompleted(isProfileComplete(user));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return Optional.of(user);
    }

    /**
     * 判断报名所需个人资料是否全部填写
     *
     * @param user 用户实体
     * @return 必填资料是否完整
     */
    private static boolean isProfileComplete(User user) {
        return StringUtils.hasText(user.getEmail())
                && StringUtils.hasText(user.getPhone())
                && StringUtils.hasText(user.getCollege())
                && StringUtils.hasText(user.getMajor())
                && user.getGrade() != null;
    }

    /**
     * 读取必填 claim；缺失或为空则抛出异常。
     */
    private static String claimRequired(Map<String, Object> claims, String key) {
        Object value = claims.get(key);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw new IllegalStateException("OIDC token 缺少必填 claim: " + key);
        }
        return String.valueOf(value).trim();
    }
}
