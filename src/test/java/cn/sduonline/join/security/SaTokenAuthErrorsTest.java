package cn.sduonline.join.security;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.sduonline.join.data.enums.BizCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class SaTokenAuthErrorsTest {

    @Test
    void missingTokenMapsToNotLogin() {
        NotLoginException exception = NotLoginException.newInstance(
                "login",
                NotLoginException.NOT_TOKEN,
                "未能读取到有效 token",
                null
        );

        assertThat(SaTokenAuthErrors.toBizCode(exception)).isEqualTo(BizCode.NOT_LOGIN);
    }

    @Test
    void expiredAndInvalidTokensMapToTokenInvalid() {
        assertThat(SaTokenAuthErrors.toBizCode(notLogin(NotLoginException.TOKEN_TIMEOUT)))
                .isEqualTo(BizCode.TOKEN_INVALID);
        assertThat(SaTokenAuthErrors.toBizCode(notLogin(NotLoginException.INVALID_TOKEN)))
                .isEqualTo(BizCode.TOKEN_INVALID);
        assertThat(SaTokenAuthErrors.toBizCode(notLogin(NotLoginException.BE_REPLACED)))
                .isEqualTo(BizCode.TOKEN_INVALID);
        assertThat(SaTokenAuthErrors.toBizCode(notLogin(NotLoginException.KICK_OUT)))
                .isEqualTo(BizCode.TOKEN_INVALID);
    }

    @Test
    void expiredTokenWritesUnauthorizedJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean written = SaTokenAuthErrors.writeIfPresent(
                response,
                notLogin(NotLoginException.TOKEN_TIMEOUT)
        );

        assertThat(written).isTrue();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":" + BizCode.TOKEN_INVALID.getCode())
                .contains("\"msg\":\"" + BizCode.TOKEN_INVALID.getMsg() + "\"")
                .contains("\"data\":null");
    }

    @Test
    void missingRoleWritesForbiddenJson() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean written = SaTokenAuthErrors.writeIfPresent(
                response,
                new NotRoleException("SYSTEM_ADMIN", "login")
        );

        assertThat(written).isTrue();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString())
                .contains("\"code\":" + BizCode.NO_PERMISSION.getCode());
    }

    private static NotLoginException notLogin(String type) {
        return NotLoginException.newInstance("login", type, "token 已过期", "aa7000");
    }
}
