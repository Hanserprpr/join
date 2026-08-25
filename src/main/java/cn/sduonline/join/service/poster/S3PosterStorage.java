package cn.sduonline.join.service.poster;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.service.avatar.AvatarFileType;
import cn.sduonline.join.service.avatar.AvatarStorageException;
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
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** S3 兼容海报存储，复用头像对象存储连接配置。 */
@Component
@ConditionalOnProperty(prefix = "app.avatar", name = "storage", havingValue = "s3")
public class S3PosterStorage implements PosterStorage {

    private final AppProperties.S3 properties;
    private final AppProperties.Poster posterProperties;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3PosterStorage(AppProperties appProperties) {
        properties = appProperties.getAvatar().getS3();
        posterProperties = appProperties.getPoster();
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
        client = S3Client.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .httpClient(UrlConnectionHttpClient.create())
                .serviceConfiguration(s3Configuration)
                .build();
        presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.getEndpoint()))
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(s3Configuration)
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
                .cacheControl("private, max-age=" + presignedUrlTtlSeconds())
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
    public String accessUrl(String storedUrl) {
        String objectKey = ownedObjectKey(storedUrl);
        if (objectKey == null) {
            return storedUrl;
        }
        long ttlSeconds = presignedUrlTtlSeconds();
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .responseCacheControl("private, max-age=" + ttlSeconds)
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(ttlSeconds))
                .getObjectRequest(objectRequest)
                .build();
        try {
            return presigner.presignGetObject(presignRequest).url().toString();
        } catch (SdkException exception) {
            throw new AvatarStorageException("生成 S3 海报预签名 URL 失败", exception);
        }
    }

    @Override
    public String publicUrlPrefix() {
        return properties.getPublicBaseUrl().replaceAll("/+$", "") + "/posters/";
    }

    @PreDestroy
    public void close() {
        client.close();
        presigner.close();
    }

    private String ownedObjectKey(String storedUrl) {
        if (!StringUtils.hasText(storedUrl)) {
            return null;
        }
        String prefix = publicUrlPrefix();
        if (!storedUrl.startsWith(prefix)) {
            return null;
        }
        String fileName = storedUrl.substring(prefix.length());
        if (!StringUtils.hasText(fileName) || fileName.contains("/")
                || fileName.contains("\\") || fileName.contains("?")
                || fileName.contains("#")) {
            return null;
        }
        return "posters/" + fileName;
    }

    private long presignedUrlTtlSeconds() {
        return Math.max(60, Math.min(
                604800, posterProperties.getPresignedUrlTtlSeconds()));
    }

    private static void requireText(String value, String environmentName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("使用 S3 海报存储时必须配置 " + environmentName);
        }
    }
}
