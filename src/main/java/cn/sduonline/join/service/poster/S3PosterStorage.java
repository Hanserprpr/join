package cn.sduonline.join.service.poster;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.service.avatar.AvatarFileType;
import cn.sduonline.join.service.avatar.AvatarStorageException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** S3 兼容海报存储，复用头像对象存储连接配置。 */
@Component
@ConditionalOnProperty(prefix = "app.avatar", name = "storage", havingValue = "s3")
public class S3PosterStorage implements PosterStorage {

    private final AppProperties.S3 properties;
    private final S3Client client;

    public S3PosterStorage(AppProperties appProperties) {
        properties = appProperties.getAvatar().getS3();
        requireText(properties.getEndpoint(), "AVATAR_S3_ENDPOINT");
        requireText(properties.getAccessKey(), "AVATAR_S3_ACCESS_KEY");
        requireText(properties.getSecretKey(), "AVATAR_S3_SECRET_KEY");
        requireText(properties.getBucket(), "AVATAR_S3_BUCKET");
        requireText(properties.getPublicBaseUrl(), "AVATAR_S3_PUBLIC_BASE_URL");
        client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.getAccessKey(), properties.getSecretKey())))
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build())
                .build();
    }

    @Override
    public String store(MultipartFile file, AvatarFileType fileType) throws IOException {
        String key = "posters/" + UUID.randomUUID().toString().replace("-", "")
                + "." + fileType.extension();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(fileType.mediaType())
                .cacheControl("public, max-age=31536000, immutable")
                .build();
        try (InputStream input = file.getInputStream()) {
            try {
                client.putObject(request, RequestBody.fromInputStream(input, file.getSize()));
            } catch (SdkException exception) {
                throw new AvatarStorageException("S3 海报上传失败", exception);
            }
        }
        return publicUrlPrefix() + key.substring("posters/".length());
    }

    @Override
    public String publicUrlPrefix() {
        return properties.getPublicBaseUrl().replaceAll("/+$", "") + "/posters/";
    }

    @PreDestroy
    public void close() {
        client.close();
    }

    private static void requireText(String value, String environmentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("使用 S3 海报存储时必须配置 " + environmentName);
        }
    }
}
