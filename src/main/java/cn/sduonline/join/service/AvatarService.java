package cn.sduonline.join.service;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.mapper.UserMapper;
import cn.sduonline.join.service.avatar.AvatarStorage;
import cn.sduonline.join.service.avatar.AvatarFileType;
import cn.sduonline.join.service.avatar.AvatarFileValidator;
import cn.sduonline.join.service.avatar.ClamAvScanner;
import cn.sduonline.join.service.avatar.AvatarStorageException;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** 用户头像上传及旧文件清理。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarService {

    private final UserMapper userMapper;
    private final AvatarStorage avatarStorage;
    private final AvatarFileValidator avatarFileValidator;
    private final ClamAvScanner clamAvScanner;

    public ServiceResult<User> updateAvatar(String casId, MultipartFile file) throws IOException {
        User user = userMapper.selectById(casId);
        if (user == null) {
            return ServiceResult.failure(BizCode.USER_NOT_FOUND);
        }
        if (StudentAccountPolicy.requiresPrimaryAccount(casId)) {
            return ServiceResult.failure(BizCode.PROFILE_UPDATE_FORBIDDEN);
        }

        AvatarFileType fileType = avatarFileValidator.validate(file);
        clamAvScanner.scan(file);
        String oldKey = user.getAvatarKey();
        String newKey = avatarStorage.store(file, fileType);
        try {
            user.setAvatarKey(newKey);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        } catch (RuntimeException exception) {
            deleteQuietly(newKey);
            throw exception;
        }
        if (oldKey != null && !oldKey.equals(newKey)) {
            deleteQuietly(oldKey);
        }
        return ServiceResult.success(user);
    }

    public String publicUrl(User user) {
        return user == null ? null : avatarStorage.publicUrl(user.getAvatarKey());
    }

    private void deleteQuietly(String key) {
        try {
            avatarStorage.delete(key);
        } catch (IOException | AvatarStorageException exception) {
            log.warn("Failed to delete avatar object, key={}", key, exception);
        }
    }
}
