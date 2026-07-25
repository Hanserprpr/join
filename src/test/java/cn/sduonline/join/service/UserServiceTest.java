package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.po.User;
import cn.sduonline.join.data.dto.ContactUpdateRequest;
import cn.sduonline.join.data.dto.ExternalStudentIdentity;
import cn.sduonline.join.mapper.UserMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userMapper);
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

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("20240001", captor.getValue().getCasId());
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
    }

    @Test
    void syncFromOidc_requiresCasIdClaim() {
        Map<String, Object> claims = baseClaims("sub-001", "张三");
        // no casID
        OidcUser oidcUser = fromClaims(claims);

        assertThrows(IllegalStateException.class, () -> userService.syncFromOidc(oidcUser));
        verify(userMapper, never()).insert(any(User.class));
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
        verify(userMapper).insert(any(User.class));
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

        User result = userService.updateContact("20240001", request).orElseThrow();

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

        User result = userService.updateContact(
                "20240001",
                new ContactUpdateRequest(null, null, null, null, null, "123456")
        ).orElseThrow();

        assertEquals("123456", result.getQq());
        assertEquals(true, result.getProfileCompleted());
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
