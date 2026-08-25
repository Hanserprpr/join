package cn.sduonline.join.service.poster;

import static org.assertj.core.api.Assertions.assertThat;

import cn.sduonline.join.config.AppProperties;
import org.junit.jupiter.api.Test;

class S3PosterStorageTest {

    @Test
    void buildsPresignedAccessUrlForOwnedPoster() {
        AppProperties properties = properties();
        properties.getPoster().setPresignedUrlTtlSeconds(1800);

        S3PosterStorage storage = new S3PosterStorage(properties);
        try {
            String url = storage.accessUrl(
                    "https://files.example.com/join/posters/example.png");

            assertThat(url)
                    .startsWith(
                            "http://127.0.0.1:9000/join/posters/example.png?")
                    .contains("X-Amz-Algorithm=AWS4-HMAC-SHA256")
                    .contains("X-Amz-Credential=access-key")
                    .contains("X-Amz-Expires=1800")
                    .contains("X-Amz-Signature=");
        } finally {
            storage.close();
        }
    }

    @Test
    void leavesWhitelistedExternalPosterUrlUnchanged() {
        AppProperties properties = properties();
        S3PosterStorage storage = new S3PosterStorage(properties);
        try {
            String externalUrl = "https://images.example.org/poster.png";
            assertThat(storage.accessUrl(externalUrl)).isEqualTo(externalUrl);
        } finally {
            storage.close();
        }
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();
        AppProperties.S3 s3 = properties.getAvatar().getS3();
        s3.setEndpoint("http://127.0.0.1:9000");
        s3.setAccessKey("access-key");
        s3.setSecretKey("secret-key");
        s3.setBucket("join");
        s3.setPublicBaseUrl("https://files.example.com/join/");
        return properties;
    }
}
