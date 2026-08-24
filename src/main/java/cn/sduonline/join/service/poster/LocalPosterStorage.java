package cn.sduonline.join.service.poster;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.service.avatar.AvatarFileType;
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

/** 开发和单机部署使用的本地海报存储。 */
@Component
@ConditionalOnProperty(prefix = "app.avatar", name = "storage",
        havingValue = "local", matchIfMissing = true)
public class LocalPosterStorage implements PosterStorage {

    private final AppProperties.Poster properties;
    private final Path root;

    public LocalPosterStorage(AppProperties appProperties) throws IOException {
        properties = appProperties.getPoster();
        root = Path.of(properties.getLocalDirectory()).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public String store(MultipartFile file, AvatarFileType fileType) throws IOException {
        String fileName = UUID.randomUUID().toString().replace("-", "")
                + "." + fileType.extension();
        Path target = root.resolve(fileName).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("非法海报对象 key");
        }
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return publicUrlPrefix() + fileName;
    }

    @Override
    public String publicUrlPrefix() {
        String baseUrl = properties.getPublicBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                    .build()
                    .toUriString();
        }
        return baseUrl.replaceAll("/+$", "") + "/uploads/posters/";
    }
}
