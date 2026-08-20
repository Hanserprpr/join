package cn.sduonline.join.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.enums.BizCode;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class SaAsyncAwareInterceptorTest {

    private StpLogic originalLogic;

    @BeforeEach
    void saveOriginalLogic() {
        originalLogic = StpUtil.getStpLogic();
    }

    @AfterEach
    void restoreOriginalLogic() {
        StpUtil.setStpLogic(originalLogic);
    }

    @Test
    void expiredLoginTokenReturnsUnifiedUnauthorizedJson() throws Exception {
        StpUtil.setStpLogic(new StubStpLogic(notLogin(NotLoginException.TOKEN_TIMEOUT)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new SaAsyncAwareInterceptor().preHandle(
                request(DispatcherType.REQUEST),
                response,
                loginHandler()
        );

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":" + BizCode.TOKEN_INVALID.getCode())
                .contains("\"msg\":\"" + BizCode.TOKEN_INVALID.getMsg() + "\"");
    }

    @Test
    void missingLoginTokenReturnsNotLoginJson() throws Exception {
        StpUtil.setStpLogic(new StubStpLogic(notLogin(NotLoginException.NOT_TOKEN)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new SaAsyncAwareInterceptor().preHandle(
                request(DispatcherType.REQUEST),
                response,
                loginHandler()
        );

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":" + BizCode.NOT_LOGIN.getCode())
                .contains("\"msg\":\"" + BizCode.NOT_LOGIN.getMsg() + "\"");
    }

    @Test
    void asyncDispatchSkipsLoginCheck() throws Exception {
        StpUtil.setStpLogic(new StubStpLogic(notLogin(NotLoginException.TOKEN_TIMEOUT)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = new SaAsyncAwareInterceptor().preHandle(
                request(DispatcherType.ASYNC),
                response,
                loginHandler()
        );

        assertThat(allowed).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString()).isEmpty();
    }

    private static MockHttpServletRequest request(DispatcherType dispatcherType) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setDispatcherType(dispatcherType);
        return request;
    }

    private static HandlerMethod loginHandler() throws NoSuchMethodException {
        return new HandlerMethod(new LoginProtectedController(), "needLogin");
    }

    private static NotLoginException notLogin(String type) {
        return NotLoginException.newInstance("login", type, "token 已过期", "aa7000");
    }

    private static final class StubStpLogic extends StpLogic {
        private final NotLoginException exception;

        private StubStpLogic(NotLoginException exception) {
            super(StpUtil.TYPE);
            this.exception = exception;
        }

        @Override
        public void checkLogin() {
            throw exception;
        }
    }

    private static final class LoginProtectedController {
        @SaCheckLogin
        public String needLogin() {
            return "ok";
        }
    }
}
