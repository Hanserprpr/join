package cn.sduonline.join.service.avatar;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.sduonline.join.config.AppProperties;
import org.junit.jupiter.api.Test;

class S3AvatarStorageTest {

    @Test
    void buildsPublicUrlFromConfiguredBaseUrl() {
        AppProperties properties = new AppProperties();
        AppProperties.S3 s3 = properties.getAvatar().getS3();
        s3.setEndpoint("http://127.0.0.1:9000");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setBucket("join");
        s3.setPublicBaseUrl("https://files.example.com/join/");

        S3AvatarStorage storage = new S3AvatarStorage(properties);
        try {
            assertEquals(
                    "https://files.example.com/join/avatars/example.png",
                    storage.publicUrl("avatars/example.png")
            );
        } finally {
            storage.close();
        }
    }
}
