package cn.sduonline.join.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import cn.sduonline.join.config.GlobalExceptionHandler;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.ProfileIncompleteException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void permissionFailureUsesUnifiedResultWithoutTrace() {
        ResponseEntity<Result<Void>> response =
                handler.handleNoPermission(new RuntimeException("internal detail"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(BizCode.NO_PERMISSION.getCode(), response.getBody().getCode());
        assertEquals(BizCode.NO_PERMISSION.getMsg(), response.getBody().getMsg());
    }

    @Test
    void profileIncompleteHasSpecificBusinessResponse() {
        ResponseEntity<Result<Void>> response =
                handler.handleProfileIncomplete(new ProfileIncompleteException());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(BizCode.PROFILE_INCOMPLETE.getCode(), response.getBody().getCode());
        assertEquals(BizCode.PROFILE_INCOMPLETE.getMsg(), response.getBody().getMsg());
    }

    @Test
    void disconnectedAsyncResponseIsIgnored() {
        handler.handleAsyncRequestNotUsable(
                new AsyncRequestNotUsableException(
                        "Response not usable after response errors."
                )
        );
    }

    @Test
    void unexpectedFailureDoesNotExposeExceptionMessage() {
        ResponseEntity<Result<Void>> response =
                handler.handleUnexpected(new RuntimeException("sensitive detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(BizCode.SYSTEM_ERROR.getCode(), response.getBody().getCode());
        assertEquals(BizCode.SYSTEM_ERROR.getMsg(), response.getBody().getMsg());
    }
}
