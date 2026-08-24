package cn.sduonline.join.service.avatar;

import cn.sduonline.join.config.AppProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** S3 兼容头像存储。 */
@Component
@ConditionalOnProperty(prefix = "app.avatar", name = "storage", havingValue = "s3")
public class S3AvatarStorage implements AvatarStorage {

    private final AppProperties.S3 properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3AvatarStorage(AppProperties appProperties) {
        this.properties = appProperties.getAvatar().getS3();
        requireText(properties.getEndpoint(), "AVATAR_S3_ENDPOINT");
        requireText(properties.getAccessKey(), "AVATAR_S3_ACCESS_KEY");
        requireText(properties.getSecretKey(), "AVATAR_S3_SECRET_KEY");
        requireText(properties.getBucket(), "AVATAR_S3_BUCKET");
        requireText(properties.getPublicBaseUrl(), "AVATAR_S3_PUBLIC_BASE_URL");

        StaticCredentialsProvider credentialsProvider =
                StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        properties.getAccessKey(), properties.getSecretKey()));
        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(properties.isPathStyleAccess())
                .build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(s3Configuration)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration)
                .build();
    }

    @Override
    public String store(MultipartFile file, AvatarFileType fileType) throws IOException {
        String key = "avatars/" + UUID.randomUUID().toString().replace("-", "")
                + "." + fileType.extension();
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .contentType(fileType.mediaType())
                .cacheControl("private, max-age=" + presignedUrlTtlSeconds())
                .build();
        try (InputStream input = file.getInputStream()) {
            try {
                client.putObject(request, RequestBody.fromInputStream(input, file.getSize()));
            } catch (SdkException exception) {
                throw new AvatarStorageException("S3 头像上传失败", exception);
            }
        }
        return key;
    }

    @Override
    public void delete(String key) {
        if (StringUtils.hasText(key)) {
            try {
                client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .build());
            } catch (SdkException exception) {
                throw new AvatarStorageException("S3 头像删除失败", exception);
            }
        }
    }

    @Override
    public String publicUrl(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        long ttlSeconds = presignedUrlTtlSeconds();
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .responseCacheControl("private, max-age=" + ttlSeconds)
                .build();
        GetObjectPresignRequest presignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(ttlSeconds))
                        .getObjectRequest(objectRequest)
                        .build();
        try {
            return presigner.presignGetObject(presignRequest)
                    .url()
                    .toString();
        } catch (SdkException exception) {
            throw new AvatarStorageException("生成 S3 头像预签名 URL 失败", exception);
        }
    }

    @PreDestroy
    public void close() {
        client.close();
        presigner.close();
    }

    private long presignedUrlTtlSeconds() {
        return Math.max(
                60, Math.min(604800, properties.getPresignedUrlTtlSeconds()));
    }

    private static void requireText(String value, String environmentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("使用 S3 头像存储时必须配置 " + environmentName);
        }
    }
}
