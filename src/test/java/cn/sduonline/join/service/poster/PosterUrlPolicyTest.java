package cn.sduonline.join.service.poster;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class PosterUrlPolicyTest {

    private static final List<String> ALLOWED = List.of(
            "https://files.example.com/join/posters/"
    );

    @Test
    void acceptsUrlUnderAllowedOriginAndPath() {
        assertTrue(PosterUrlPolicy.isAllowed(
                "https://files.example.com/join/posters/example.png", ALLOWED
        ));
    }

    @Test
    void rejectsLookalikeHostAndSiblingPath() {
        assertFalse(PosterUrlPolicy.isAllowed(
                "https://files.example.com.evil.test/join/posters/example.png", ALLOWED
        ));
        assertFalse(PosterUrlPolicy.isAllowed(
                "https://files.example.com/join/posters-evil/example.png", ALLOWED
        ));
    }

    @Test
    void rejectsCredentialsQueriesAndEncodedTraversal() {
        assertFalse(PosterUrlPolicy.isAllowed(
                "https://user@files.example.com/join/posters/example.png", ALLOWED
        ));
        assertFalse(PosterUrlPolicy.isAllowed(
                "https://files.example.com/join/posters/example.png?token=x", ALLOWED
        ));
        assertFalse(PosterUrlPolicy.isAllowed(
                "https://files.example.com/join/posters/%2e%2e/secret", ALLOWED
        ));
    }
}
