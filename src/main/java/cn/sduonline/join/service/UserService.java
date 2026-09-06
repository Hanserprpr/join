package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.data.dto.ContactUpdateRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private final CollegeMajorService collegeMajorService;

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
     * 根据 OIDC claims 创建本地用户。
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
            normalizeRestrictedAccount(existing, now);
            return existing;
        }

        User created = new User();
        created.setSub(sub);
        created.setName(StudentAccountPolicy.requiresPrimaryAccount(casId)
                ? StudentAccountPolicy.PRIMARY_ACCOUNT_REQUIRED_NAME
                : name);
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
     * 根据可信外部系统身份按学号创建用户。
     * 使用读已提交，确保主键冲突后的查询能看到其他事务刚创建的用户。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public User syncFromExternal(ExternalStudentIdentity identity) {
        String casId = identity.studentNumber().trim();
        LocalDateTime now = LocalDateTime.now();
        User existing = userMapper.selectById(casId);

        if (existing != null) {
            normalizeRestrictedAccount(existing, now);
            return existing;
        }

        User created = new User();
        created.setCasId(casId);
        created.setName(StudentAccountPolicy.requiresPrimaryAccount(casId)
                ? StudentAccountPolicy.PRIMARY_ACCOUNT_REQUIRED_NAME
                : identity.name().trim());
        created.setCollege(identity.college().trim());
        created.setMajor(identity.major().trim());
        created.setCampus(collegeMajorService.resolveCampus(created.getCollege()));
        created.setEmail(null);
        created.setPhone(null);
        created.setProfileCompleted(false);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        try {
            userMapper.insert(created);
        } catch (DuplicateKeyException e) {
            User concurrentlyCreated = userMapper.selectById(casId);
            if (concurrentlyCreated == null) {
                throw e;
            }
            return concurrentlyCreated;
        }
        log.info("Created local user from external identity, casId={}", casId);
        return created;
    }

    /**
     * 更新请求中提供的个人资料
     * 未提供的字段保持不变
     * <p>
     * 选填字段（邮箱、QQ 号）传空字符串表示清空，写入 null。
     * <p>
     * 提供学院或专业时，校验其是否属于系统维护的学院专业字典；
     * 校区不采信请求，按学院从字典推导，详见 {@link #applyCampus}。
     *
     * @param casId 当前用户统一认证账号
     * @param request 个人资料更新请求
     * @return 更新后的用户；用户不存在或学院专业非法时返回对应业务错误
     */
    @Transactional
    public ServiceResult<User> updateContact(
            String casId,
            ContactUpdateRequest request
    ) {
        User user = userMapper.selectById(casId);
        if (user == null) {
            return ServiceResult.failure(BizCode.USER_NOT_FOUND);
        }
        if (StudentAccountPolicy.requiresPrimaryAccount(casId)) {
            return ServiceResult.failure(BizCode.PROFILE_UPDATE_FORBIDDEN);
        }
        ServiceResult<User> validation = validateCollegeMajor(user, request);
        if (validation != null) {
            return validation;
        }
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase();
            user.setEmail(email.isEmpty() ? null : email);
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
        applyCampus(user, request.campus());
        if (request.grade() != null) {
            user.setGrade(request.grade());
        }
        if (request.qq() != null) {
            String qq = request.qq().trim();
            user.setQq(qq.isEmpty() ? null : qq);
        }
        user.setProfileCompleted(isProfileComplete(user));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return ServiceResult.success(user);
    }

    /**
     * 按学院推导并写入校区。
     * <p>
     * 校区由学院唯一决定，字典能推导出结果时以推导值为准；请求里的校区只在
     * 字典未给出该学院校区时兜底，保证旧客户端继续可用。学院未变的更新也会
     * 重新推导一次，历史资料因此会在用户下次改资料时自动补齐校区。
     *
     * @param user 待更新的用户
     * @param requested 请求中携带的校区，可为 null
     */
    private void applyCampus(User user, Campus requested) {
        Campus derived = collegeMajorService.resolveCampus(user.getCollege());
        if (derived != null) {
            user.setCampus(derived);
        } else if (requested != null) {
            user.setCampus(requested);
        }
    }

    /** 登录时同步修正历史非主修账号的展示姓名。 */
    private void normalizeRestrictedAccount(User user, LocalDateTime now) {
        if (!StudentAccountPolicy.requiresPrimaryAccount(user.getCasId())
                || StudentAccountPolicy.PRIMARY_ACCOUNT_REQUIRED_NAME.equals(
                        user.getName())) {
            return;
        }
        user.setName(StudentAccountPolicy.PRIMARY_ACCOUNT_REQUIRED_NAME);
        user.setUpdatedAt(now);
        userMapper.updateById(user);
    }

    /**
     * 校验请求中的学院与专业是否合法
     * 仅在请求提供学院或专业时触发，缺省字段回退到用户当前值
     *
     * @param user 当前用户
     * @param request 个人资料更新请求
     * @return 校验通过返回 null，否则返回对应业务错误
     */
    private ServiceResult<User> validateCollegeMajor(
            User user,
            ContactUpdateRequest request
    ) {
        if (!StringUtils.hasText(request.college())
                && !StringUtils.hasText(request.major())) {
            return null;
        }
        String college = StringUtils.hasText(request.college())
                ? request.college().trim()
                : user.getCollege();
        String major = StringUtils.hasText(request.major())
                ? request.major().trim()
                : user.getMajor();
        if (!collegeMajorService.isValidCollege(college)) {
            return ServiceResult.failure(BizCode.COLLEGE_INVALID);
        }
        if (StringUtils.hasText(major)
                && !collegeMajorService.isValidCollegeMajor(college, major)) {
            return ServiceResult.failure(BizCode.MAJOR_INVALID);
        }
        return null;
    }

    /**
     * 判断报名所需个人资料是否全部填写
     *
     * @param user 用户实体
     * @return 必填资料是否完整
     */
    private static boolean isProfileComplete(User user) {
        return StringUtils.hasText(user.getPhone())
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
