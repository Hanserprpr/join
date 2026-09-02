package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import cn.sduonline.join.service.avatar.AvatarFileValidator;
import cn.sduonline.join.service.avatar.AvatarStorage;
import cn.sduonline.join.service.avatar.ClamAvScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class AvatarServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private AvatarStorage avatarStorage;
    @Mock
    private AvatarFileValidator avatarFileValidator;
    @Mock
    private ClamAvScanner clamAvScanner;
    @Mock
    private MultipartFile file;

    @InjectMocks
    private AvatarService avatarService;

    @Test
    void updateAvatarRejectsAccountContainingLettersBeforeProcessingFile()
            throws Exception {
        User user = new User();
        user.setCasId("2024A001");
        when(userMapper.selectById("2024A001")).thenReturn(user);

        ServiceResult<User> result = avatarService.updateAvatar(
                "2024A001", file);

        assertEquals(BizCode.PROFILE_UPDATE_FORBIDDEN, result.error());
        verifyNoInteractions(avatarFileValidator, clamAvScanner, avatarStorage);
    }
}
