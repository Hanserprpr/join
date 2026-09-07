package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.UserProfileVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.data.dto.ContactUpdateRequest;
import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.data.dto.StudentAcademicProfile;
import cn.sduonline.join.mapper.UserMapper;
import cn.sduonline.join.mapper.StudentAcademicProfileMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    /**
     * 校区由学院推导：老客户端不传校区照样能补齐，传了也不能推翻字典。
     * 校区不参与资料完整性判断，老请求的行为不受影响。
     */
    @Test
    void updateContactDerivesCampusFromCollege() {
        User user = new User();
        user.setCasId("20240001");
        user.setPhone("13900000000");
        user.setCollege("软件学院");
        user.setMajor("软件工程");
        user.setGrade(2024);
        when(userMapper.selectById("20240001")).thenReturn(user);

        var oldRequest = new ContactUpdateRequest(null, null, null, null, null, "123456");
        assertTrue(userService.updateContact("20240001", oldRequest).isSuccess());
        assertTrue(user.getProfileCompleted());
        assertEquals(Campus.SOFTWARE_PARK, user.getCampus());

        assertTrue(userService.updateContact("20240001", new ContactUpdateRequest(
                null, null, null, null, null, null, Campus.CENTRAL)).isSuccess());
        assertEquals(Campus.SOFTWARE_PARK, user.getCampus());

        assertTrue(userService.updateContact("20240001", new ContactUpdateRequest(
                null, null, "药学院", "药学", null, null)).isSuccess());
        assertEquals(Campus.BAOTUQUAN, user.getCampus());
        assertTrue(user.getProfileCompleted());
        assertEquals(Campus.BAOTUQUAN, UserProfileVO.from(user).getCampus());
    }

    /** 字典没给校区的学院（老格式配置）仍然采信请求里的校区。 */
    @Test
    void updateContactFallsBackToRequestedCampusWithoutDictionaryCampus() {
        CollegeMajorService dictionary = new CollegeMajorService();
        dictionary.replaceFromJson("{\"软件学院\": [\"软件工程\"]}");
        UserService service = new UserService(userMapper, dictionary, studentAcademicProfileMapper);
        User user = new User();
        user.setCasId("20240001");
        user.setCollege("软件学院");
        when(userMapper.selectById("20240001")).thenReturn(user);

        assertTrue(service.updateContact("20240001", new ContactUpdateRequest(
                null, null, null, null, null, null, Campus.CENTRAL)).isSuccess());

        assertEquals(Campus.CENTRAL, user.getCampus());
    }

    @Mock
    private UserMapper userMapper;

    @Mock
    private StudentAcademicProfileMapper studentAcademicProfileMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userMapper,
                new CollegeMajorService(),
                studentAcademicProfileMapper
        );
    }

    @Test
    void syncFromOidc_createsUserOnFirstLogin() {
        when(userMapper.selectById("20240001")).thenReturn(null);

        OidcUser oidcUser = oidcUser("sub-001", "张三", "20240001");

        User result = userService.syncFromOidc(oidcUser);

        assertEquals("sub-001", result.getSub());
        assertEquals("张三", result.getName());
        assertEquals("20240001", result.getCasId());
        assertEquals(null, result.getEmail());
        assertEquals(null, result.getPhone());
        assertNotNull(result.getCreatedAt());
        assertNull(result.getCollege());
        assertNull(result.getMajor());
        assertFalse(result.getProfileCompleted());
        verify(studentAcademicProfileMapper).selectByCasId("20240001");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("20240001", captor.getValue().getCasId());
    }

    @Test
    void syncFromOidc_prefillsOnlyAcademicFieldsAndDerivesCampus() {
        when(studentAcademicProfileMapper.selectByCasId("20240001"))
                .thenReturn(new StudentAcademicProfile(" 软件学院 ", " 软件工程 "));

        User result = userService.syncFromOidc(oidcUser("sub-001", "张三", " 20240001 "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertSame(result, captor.getValue());
        assertEquals("软件学院", result.getCollege());
        assertEquals("软件工程", result.getMajor());
        assertEquals(Campus.SOFTWARE_PARK, result.getCampus());
        assertEquals("张三", result.getName());
        assertNull(result.getEmail());
        assertNull(result.getPhone());
        assertNull(result.getGrade());
        assertFalse(result.getProfileCompleted());
    }

    @Test
    void syncFromOidc_continuesWhenAcademicLookupFails() {
        when(studentAcademicProfileMapper.selectByCasId("20240001"))
                .thenThrow(new DataAccessResourceFailureException("source unavailable"));

        User result = userService.syncFromOidc(oidcUser("sub-001", "张三", "20240001"));

        verify(userMapper).insert(result);
        assertNull(result.getCollege());
        assertNull(result.getMajor());
        assertFalse(result.getProfileCompleted());
    }

    @Test
    void syncFromOidc_treatsBlankAcademicFieldsAsMissing() {
        when(studentAcademicProfileMapper.selectByCasId("20240001"))
                .thenReturn(new StudentAcademicProfile("  ", null));

        User result = userService.syncFromOidc(oidcUser("sub-001", "张三", "20240001"));

        verify(userMapper).insert(result);
        assertNull(result.getCollege());
        assertNull(result.getMajor());
        assertNull(result.getCampus());
    }

    @Test
    void syncFromOidc_skipsOversizedFieldWithoutDiscardingOtherField() {
        when(studentAcademicProfileMapper.selectByCasId("20240001"))
                .thenReturn(new StudentAcademicProfile("软件学院", "专".repeat(65)));

        User result = userService.syncFromOidc(oidcUser("sub-001", "张三", "20240001"));

        verify(userMapper).insert(result);
        assertEquals("软件学院", result.getCollege());
        assertNull(result.getMajor());
    }

    @Test
    void syncFromOidc_doesNotUpdateExistingUser() {
        User existing = new User();
        existing.setSub("sub-001");
        existing.setName("旧名");
        existing.setCasId("20240001");
        existing.setEmail("old@mail.sdu.edu.cn");
        existing.setPhone("13900000000");
        existing.setCollege("计算机科学与技术学院");
        existing.setMajor("软件工程");
        existing.setGrade(2024);

        when(userMapper.selectById("20240001")).thenReturn(existing);

        OidcUser oidcUser = oidcUser("sub-001", "新名", "20240001");

        User result = userService.syncFromOidc(oidcUser);

        assertEquals("旧名", result.getName());
        assertEquals("old@mail.sdu.edu.cn", result.getEmail());
        assertEquals("13900000000", result.getPhone());
        assertEquals("计算机科学与技术学院", result.getCollege());
        assertEquals("软件工程", result.getMajor());
        assertEquals(2024, result.getGrade());
        verify(userMapper, never()).updateById(any(User.class));
        verifyNoInteractions(studentAcademicProfileMapper);
    }

    @Test
    void syncFromOidc_marksNewAccountContainingLettersAsRestricted() {
        when(userMapper.selectById("2024A001")).thenReturn(null);

        User result = userService.syncFromOidc(
                oidcUser("sub-001", "张三", "2024A001"));

        assertEquals("请使用主修账号进入", result.getName());
        verify(userMapper).insert(result);
    }

    @Test
    void syncFromOidc_correctsPreviouslyRegisteredAccountContainingLetters() {
        User existing = new User();
        existing.setCasId("2024a001");
        existing.setName("历史姓名");
        when(userMapper.selectById("2024a001")).thenReturn(existing);

        User result = userService.syncFromOidc(
                oidcUser("sub-001", "新姓名", "2024a001"));

        assertEquals("请使用主修账号进入", result.getName());
        assertNotNull(result.getUpdatedAt());
        verify(userMapper).updateById(existing);
    }

    @Test
    void syncFromOidc_requiresCasIdClaim() {
        Map<String, Object> claims = baseClaims("sub-001", "张三");
        // no casID
        OidcUser oidcUser = fromClaims(claims);

        assertThrows(IllegalStateException.class, () -> userService.syncFromOidc(oidcUser));
        verify(userMapper, never()).insert(any(User.class));
        verifyNoInteractions(studentAcademicProfileMapper);
    }

    @Test
    void syncFromExternal_createsUserUsingCasIdAsPrimaryKey() {
        when(userMapper.selectById("20240001")).thenReturn(null);

        User result = userService.syncFromExternal(new ExternalStudentIdentity(
                "20240001", "张三", "软件学院", "软件工程"
        ));

        assertEquals("20240001", result.getCasId());
        assertEquals("张三", result.getName());
        assertEquals("软件学院", result.getCollege());
        assertEquals("软件工程", result.getMajor());
        assertEquals(Campus.SOFTWARE_PARK, result.getCampus());
        verify(userMapper).insert(any(User.class));
        verifyNoInteractions(studentAcademicProfileMapper);
    }

    @Test
    void syncFromExternal_returnsConcurrentlyCreatedUserAfterDuplicateKey() {
        User existing = new User();
        existing.setCasId("20240001");
        existing.setName("已保存的姓名");
        existing.setProfileCompleted(true);
        when(userMapper.selectById("20240001")).thenReturn(null, existing);
        when(userMapper.insert(any(User.class)))
                .thenThrow(new DuplicateKeyException("duplicate primary key"));

        User result = userService.syncFromExternal(new ExternalStudentIdentity(
                "20240001", "张三", "软件学院", "软件工程"));

        assertSame(existing, result);
        assertEquals("已保存的姓名", result.getName());
        assertTrue(result.getProfileCompleted());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void syncFromExternal_rethrowsDuplicateKeyWhenUserStillMissing() {
        DuplicateKeyException failure = new DuplicateKeyException("other unique constraint");
        when(userMapper.selectById("20240001")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenThrow(failure);

        DuplicateKeyException thrown = assertThrows(DuplicateKeyException.class,
                () -> userService.syncFromExternal(new ExternalStudentIdentity(
                        "20240001", "张三", "软件学院", "软件工程")));

        assertSame(failure, thrown);
    }

    @Test
    void syncFromExternal_marksAccountContainingLettersAsRestricted() {
        when(userMapper.selectById("fx20240001")).thenReturn(null);

        User result = userService.syncFromExternal(new ExternalStudentIdentity(
                "fx20240001", "张三", "软件学院", "软件工程"
        ));

        assertEquals("请使用主修账号进入", result.getName());
        verify(userMapper).insert(result);
    }

    @Test
    void updateContactCompletesProfileWithoutQq() {
        User user = new User();
        user.setCasId("20240001");
        when(userMapper.selectById("20240001")).thenReturn(user);
        ContactUpdateRequest request = new ContactUpdateRequest(
                "STUDENT@SDU.EDU.CN",
                "13900000000",
                "软件学院",
                "软件工程",
                2024,
                null
        );

        ServiceResult<User> outcome = userService.updateContact("20240001", request);

        assertTrue(outcome.isSuccess());
        User result = outcome.data();
        assertEquals("student@sdu.edu.cn", result.getEmail());
        assertEquals("13900000000", result.getPhone());
        assertEquals("软件学院", result.getCollege());
        assertEquals("软件工程", result.getMajor());
        assertEquals(2024, result.getGrade());
        assertEquals(null, result.getQq());
        assertEquals(true, result.getProfileCompleted());
        verify(userMapper).updateById(user);
    }

    @Test
    void updateContactRejectsAccountContainingLetters() {
        User user = new User();
        user.setCasId("2024A001");
        when(userMapper.selectById("2024A001")).thenReturn(user);

        ServiceResult<User> outcome = userService.updateContact(
                "2024A001",
                new ContactUpdateRequest(
                        null, "13900000000", null, null, null, null)
        );

        assertEquals(BizCode.PROFILE_UPDATE_FORBIDDEN, outcome.error());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void updateContactCompletesProfileWithoutEmail() {
        User user = new User();
        user.setCasId("20240001");
        when(userMapper.selectById("20240001")).thenReturn(user);
        ContactUpdateRequest request = new ContactUpdateRequest(
                null,
                "13900000000",
                "软件学院",
                "软件工程",
                2024,
                null
        );

        ServiceResult<User> outcome = userService.updateContact(
                "20240001", request
        );

        assertTrue(outcome.isSuccess());
        assertEquals(null, outcome.data().getEmail());
        assertEquals(true, outcome.data().getProfileCompleted());
        verify(userMapper).updateById(user);
    }

    @Test
    void updateContactAcceptsOptionalQqForCompletedProfile() {
        User user = new User();
        user.setCasId("20240001");
        user.setEmail("student@sdu.edu.cn");
        user.setPhone("13900000000");
        user.setCollege("软件学院");
        user.setMajor("软件工程");
        user.setGrade(2024);
        user.setProfileCompleted(true);
        when(userMapper.selectById("20240001")).thenReturn(user);

        ServiceResult<User> outcome = userService.updateContact(
                "20240001",
                new ContactUpdateRequest(null, null, null, null, null, "123456")
        );

        assertTrue(outcome.isSuccess());
        User result = outcome.data();
        assertEquals("123456", result.getQq());
        assertEquals(true, result.getProfileCompleted());
    }

    @Test
    void updateContactClearsEmailAndQqOnEmptyString() {
        User user = new User();
        user.setCasId("20240001");
        user.setEmail("student@sdu.edu.cn");
        user.setPhone("13900000000");
        user.setCollege("软件学院");
        user.setMajor("软件工程");
        user.setGrade(2024);
        user.setQq("123456");
        user.setProfileCompleted(true);
        when(userMapper.selectById("20240001")).thenReturn(user);

        ServiceResult<User> outcome = userService.updateContact(
                "20240001",
                new ContactUpdateRequest("", null, null, null, null, "")
        );

        assertTrue(outcome.isSuccess());
        User result = outcome.data();
        assertEquals(null, result.getEmail());
        assertEquals(null, result.getQq());
        // 邮箱与 QQ 不属于必填资料，清空后资料仍然完整
        assertEquals(true, result.getProfileCompleted());
        assertEquals("13900000000", result.getPhone());
        verify(userMapper).updateById(user);
    }

    @Test
    void updateContactKeepsEmailAndQqOnNull() {
        User user = new User();
        user.setCasId("20240001");
        user.setEmail("student@sdu.edu.cn");
        user.setPhone("13900000000");
        user.setQq("123456");
        when(userMapper.selectById("20240001")).thenReturn(user);

        ServiceResult<User> outcome = userService.updateContact(
                "20240001",
                new ContactUpdateRequest(null, "13800000000", null, null, null, null)
        );

        assertTrue(outcome.isSuccess());
        assertEquals("student@sdu.edu.cn", outcome.data().getEmail());
        assertEquals("123456", outcome.data().getQq());
        assertEquals("13800000000", outcome.data().getPhone());
    }

    /**
     * 必填字段传空字符串不会被清空。
     * <p>
     * 其中 phone 的空串经 API 到不了这里——{@code @Pattern("^1\\d{10}$")}
     * 会先返回 400；college、major 的 {@code @Size} 则放行空串。这里钉的是
     * service 自身的兜底行为，避免日后放宽校验时把必填字段一起清掉。
     */
    @Test
    void updateContactKeepsRequiredFieldsOnEmptyString() {
        User user = new User();
        user.setCasId("20240001");
        user.setPhone("13900000000");
        user.setCollege("软件学院");
        user.setMajor("软件工程");
        user.setGrade(2024);
        user.setProfileCompleted(true);
        when(userMapper.selectById("20240001")).thenReturn(user);

        ServiceResult<User> outcome = userService.updateContact(
                "20240001",
                new ContactUpdateRequest("", "", "", "", null, null)
        );

        assertTrue(outcome.isSuccess());
        User result = outcome.data();
        assertEquals("13900000000", result.getPhone());
        assertEquals("软件学院", result.getCollege());
        assertEquals("软件工程", result.getMajor());
        assertEquals(2024, result.getGrade());
        assertEquals(true, result.getProfileCompleted());
    }

    @Test
    void updateContactRejectsUnknownCollege() {
        User user = new User();
        user.setCasId("20240001");
        when(userMapper.selectById("20240001")).thenReturn(user);
        ContactUpdateRequest request = new ContactUpdateRequest(
                "student@sdu.edu.cn", "13900000000", "不存在学院", "软件工程", 2024, null
        );

        ServiceResult<User> outcome = userService.updateContact("20240001", request);

        assertEquals(BizCode.COLLEGE_INVALID, outcome.error());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void updateContactRejectsMajorNotInCollege() {
        User user = new User();
        user.setCasId("20240001");
        when(userMapper.selectById("20240001")).thenReturn(user);
        ContactUpdateRequest request = new ContactUpdateRequest(
                "student@sdu.edu.cn", "13900000000", "软件学院", "临床医学（五年制）", 2024, null
        );

        ServiceResult<User> outcome = userService.updateContact("20240001", request);

        assertEquals(BizCode.MAJOR_INVALID, outcome.error());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void updateContactValidatesMajorAgainstExistingCollege() {
        User user = new User();
        user.setCasId("20240001");
        user.setCollege("软件学院");
        when(userMapper.selectById("20240001")).thenReturn(user);
        ContactUpdateRequest request = new ContactUpdateRequest(
                null, null, null, "临床医学（五年制）", null, null
        );

        ServiceResult<User> outcome = userService.updateContact("20240001", request);

        assertEquals(BizCode.MAJOR_INVALID, outcome.error());
        verify(userMapper, never()).updateById(any(User.class));
    }

    private static OidcUser oidcUser(String sub, String name, String casId) {
        Map<String, Object> claims = baseClaims(sub, name);
        claims.put("casID", casId);
        return fromClaims(claims);
    }

    private static Map<String, Object> baseClaims(String sub, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", sub);
        claims.put("name", name);
        claims.put("iss", "https://i.sdu.edu.cn/pass-api");
        claims.put("iat", Instant.now().getEpochSecond());
        claims.put("exp", Instant.now().plusSeconds(3600).getEpochSecond());
        return claims;
    }

    private static OidcUser fromClaims(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                claims
        );
        return new DefaultOidcUser(null, idToken);
    }
}
