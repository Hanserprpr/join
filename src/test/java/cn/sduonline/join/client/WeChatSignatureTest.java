package cn.sduonline.join.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeChatSignatureTest {

    @Test
    void matchesSha1OfSortedTokenTimestampNonce() {
        boolean result = WeChatSignature.matches(
                "test-token", "51eceab7903acb17f5057dfac60fa898414d9553",
                "1700000000", "123456");

        assertThat(result).isTrue();
    }

    @Test
    void rejectsWrongSignature() {
        boolean result = WeChatSignature.matches(
                "test-token", "not-the-real-signature", "1700000000", "123456");

        assertThat(result).isFalse();
    }

    @Test
    void rejectsMissingParameters() {
        assertThat(WeChatSignature.matches(null, "sig", "ts", "nonce")).isFalse();
        assertThat(WeChatSignature.matches("token", "", "ts", "nonce")).isFalse();
    }
}
