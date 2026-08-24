package cn.sduonline.join.service.avatar;

import static org.assertj.core.api.Assertions.assertThat;

import cn.sduonline.join.config.AppProperties;
import org.junit.jupiter.api.Test;

class S3AvatarStorageTest {

    @Test
    void buildsPresignedUrlForPrivateBucket() {
        AppProperties properties = new AppProperties();
        AppProperties.S3 s3 = properties.getAvatar().getS3();
        s3.setEndpoint("http://127.0.0.1:9000");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setBucket("join");
        s3.setPublicBaseUrl("https://files.example.com/join/");
        s3.setPresignedUrlTtlSeconds(1800);

        S3AvatarStorage storage = new S3AvatarStorage(properties);
        try {
            String url = storage.publicUrl("avatars/example.png");
            assertThat(url)
                    .startsWith(
                            "http://127.0.0.1:9000/join/avatars/example.png?")
                    .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                    .contains("X-Amz-Credential=access-key")
                    .contains("X-Amz-Expires=1800")
                    .contains("X-Amz-Signature=");
        } finally {
            storage.close();
        }
    }
}
