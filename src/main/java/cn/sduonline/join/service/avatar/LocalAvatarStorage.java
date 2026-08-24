package cn.sduonline.join.service.avatar;

import cn.sduonline.join.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** 默认的本地磁盘头像存储，适合开发和单机部署。 */
@Component
@ConditionalOnProperty(prefix = "app.avatar", name = "storage",
        havingValue = "local", matchIfMissing = true)
public class LocalAvatarStorage implements AvatarStorage {

    private final AppProperties.Avatar properties;
    private final Path root;

    public LocalAvatarStorage(AppProperties appProperties) throws IOException {
        this.properties = appProperties.getAvatar();
        this.root = Path.of(properties.getLocalDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(MultipartFile file, AvatarFileType fileType) throws IOException {
        String key = UUID.randomUUID().toString().replace("-", "")
                + "." + fileType.extension();
        Path target = resolveSafely(key);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return key;
    }

    @Override
    public void delete(String key) throws IOException {
        if (StringUtils.hasText(key)) {
            Files.deleteIfExists(resolveSafely(key));
        }
    }

    @Override
    public String publicUrl(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String baseUrl = properties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .build()
                    .toUriString();
        }
        return baseUrl.replaceAll("/+$", "") + "/uploads/avatars/" + key;
    }

    private Path resolveSafely(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.getParent().equals(root)) {
            throw new IllegalArgumentException("非法头像对象 key");
        }
        return path;
    }

}
