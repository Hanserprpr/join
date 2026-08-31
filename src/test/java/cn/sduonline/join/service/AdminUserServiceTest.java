package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.AdminUserVO;
import cn.sduonline.join.data.dto.PageVO;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AvatarService avatarService;

    private AdminUserService adminUserService;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserService(userMapper, avatarService);
    }

    @Test
    void findUsersTranslatesPageNumberToOffset() {
        when(userMapper.countUsers(
                null, null, null, null, null, null
        )).thenReturn(45L);
        when(userMapper.selectUsers(
                null, null, null, null, null, null,
                "createdAt", "desc", 40, 20
        )).thenReturn(List.of(user("20240001")));

        ServiceResult<PageVO<AdminUserVO>> result = adminUserService.findUsers(
                null, null, null, null, null, null,
                "createdAt", "desc", 3, 20
        );

        assertTrue(result.isSuccess());
        PageVO<AdminUserVO> page = result.data();
        assertEquals(45L, page.total());
        assertEquals(3, page.totalPages());
        assertEquals(3, page.page());
        assertEquals(20, page.size());
        assertEquals("20240001", page.items().getFirst().casId());
    }

    @Test
    void findUsersNormalizesBlankFiltersToNull() {
        when(userMapper.countUsers(
                null, null, null, 2024, Boolean.TRUE, Boolean.FALSE
        )).thenReturn(0L);

        ServiceResult<PageVO<AdminUserVO>> result = adminUserService.findUsers(
                "   ", "  ", "", 2024, Boolean.TRUE, Boolean.FALSE,
                "casId", "asc", 1, 20
        );

        assertTrue(result.isSuccess());
        assertEquals(0, page(result).totalPages());
        assertTrue(page(result).items().isEmpty());
    }

    @Test
    void findUsersEscapesLikeWildcardsInKeyword() {
        when(userMapper.countUsers(
                "100!%!_!!", null, null, null, null, null
        )).thenReturn(1L);
        when(userMapper.selectUsers(
                eq("100!%!_!!"), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt()
        )).thenReturn(List.of(user("100%_!")));

        ServiceResult<PageVO<AdminUserVO>> result = adminUserService.findUsers(
                " 100%_! ", null, null, null, null, null,
                "createdAt", "desc", 1, 20
        );

        assertEquals(1, page(result).items().size());
    }

    @Test
    void findUsersSkipsRowQueryWhenNothingMatches() {
        when(userMapper.countUsers(
                "nobody", null, null, null, null, null
        )).thenReturn(0L);

        ServiceResult<PageVO<AdminUserVO>> result = adminUserService.findUsers(
                "nobody", null, null, null, null, null,
                "createdAt", "desc", 1, 20
        );

        assertTrue(page(result).items().isEmpty());
        assertEquals(0L, page(result).total());
        verify(userMapper, never()).selectUsers(
                any(), any(), any(), any(), any(), any(),
                any(), any(), anyInt(), anyInt()
        );
    }

    @Test
    void findUsersExposesBindingFlagsButNotCredentials() {
        User bound = user("20240002");
        bound.setWechatOpenid("openid-1");
        bound.setProfileCompleted(true);
        when(userMapper.countUsers(
                null, null, null, null, null, null
        )).thenReturn(1L);
        when(userMapper.selectUsers(
                null, null, null, null, null, null,
                "createdAt", "desc", 0, 20
        )).thenReturn(List.of(bound));
        when(avatarService.publicUrl(bound)).thenReturn("https://cdn/a.png");

        AdminUserVO vo = page(adminUserService.findUsers(
                null, null, null, null, null, null,
                "createdAt", "desc", 1, 20
        )).items().getFirst();

        assertTrue(vo.wechatBound());
        assertTrue(vo.profileCompleted());
        assertEquals("https://cdn/a.png", vo.avatarUrl());
    }

    /** 列表是管理页面数据，不应暴露 OpenID、OIDC subject 等身份凭据。 */
    @Test
    void adminUserViewOmitsIdentityCredentials() {
        List<String> components = Arrays
                .stream(AdminUserVO.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertFalse(components.contains("wechatOpenid"));
        assertFalse(components.contains("sub"));
        assertFalse(components.contains("avatarKey"));
    }

    private static PageVO<AdminUserVO> page(
            ServiceResult<PageVO<AdminUserVO>> result
    ) {
        assertTrue(result.isSuccess());
        return result.data();
    }

    private static User user(String casId) {
        User user = new User();
        user.setCasId(casId);
        user.setSub("sub-" + casId);
        user.setName("张三");
        user.setProfileCompleted(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
