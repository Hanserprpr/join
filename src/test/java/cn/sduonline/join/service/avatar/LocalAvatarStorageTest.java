package cn.sduonline.join.service.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.enums.BizCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalAvatarStorageTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesPngUsingGeneratedKeyAndBuildsPublicUrl() throws Exception {
        AppProperties properties = properties(1024);
        LocalAvatarStorage storage = new LocalAvatarStorage(properties);
        AvatarFileValidator validator = new AvatarFileValidator(properties);
        byte[] png = new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.txt", "text/plain", png
        );

        String key = storage.store(file, validator.validate(file));

        assertTrue(key.matches("[0-9a-f]{32}\\.png"));
        assertTrue(Files.exists(tempDirectory.resolve(key)));
        assertEquals("https://join.example/uploads/avatars/" + key,
                storage.publicUrl(key));
    }

    @Test
    void rejectsUnsupportedContentRegardlessOfClaimedMimeType() throws Exception {
        AppProperties properties = properties(1024);
        AvatarFileValidator validator = new AvatarFileValidator(properties);

        AvatarValidationException exception = assertThrows(
                AvatarValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file", "avatar.png", "image/png", "not-an-image".getBytes()
                ))
        );

        assertEquals(BizCode.AVATAR_INVALID, exception.getBizCode());
    }

    @Test
    void rejectsFilesOverConfiguredLimit() throws Exception {
        AppProperties properties = properties(4);
        AvatarFileValidator validator = new AvatarFileValidator(properties);

        AvatarValidationException exception = assertThrows(
                AvatarValidationException.class,
                () -> validator.validate(new MockMultipartFile(
                        "file", "avatar.jpg", "image/jpeg",
                        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2}
                ))
        );

        assertEquals(BizCode.AVATAR_TOO_LARGE, exception.getBizCode());
    }

    private AppProperties properties(long maxSize) {
        AppProperties properties = new AppProperties();
        properties.getAvatar().setLocalDirectory(tempDirectory.toString());
        properties.getAvatar().setPublicBaseUrl("https://join.example");
        properties.getAvatar().setMaxSizeBytes(maxSize);
        return properties;
    }
}
