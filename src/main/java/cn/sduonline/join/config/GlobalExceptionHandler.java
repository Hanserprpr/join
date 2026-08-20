package cn.sduonline.join.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.ProfileIncompleteException;
import cn.sduonline.join.security.SaTokenAuthErrors;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * REST 接口统一异常出口。异常详情只记录在服务端日志，不返回堆栈给调用方。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException exception) {
        return response(HttpStatus.UNAUTHORIZED, SaTokenAuthErrors.toBizCode(exception));
    }

    @ExceptionHandler(ProfileIncompleteException.class)
    public ResponseEntity<Result<Void>> handleProfileIncomplete(
            ProfileIncompleteException exception
    ) {
        return response(HttpStatus.FORBIDDEN, BizCode.PROFILE_INCOMPLETE);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> handleNoPermission(RuntimeException exception) {
        return response(HttpStatus.FORBIDDEN, BizCode.NO_PERMISSION);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        FieldError error = exception.getBindingResult().getFieldErrors()
                .stream()
                .findFirst()
                .orElse(null);
        String detail = error == null
                ? "请求参数校验失败"
                : error.getField() + " " + error.getDefaultMessage();
        return response(HttpStatus.BAD_REQUEST, BizCode.PARAM_INVALID, detail);
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Result<Void>> handleInvalidParameter(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, BizCode.PARAM_INVALID);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(
            MissingServletRequestParameterException exception
    ) {
        return response(
                HttpStatus.BAD_REQUEST,
                BizCode.PARAM_INVALID,
                exception.getParameterName()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException exception) {
        return response(HttpStatus.NOT_FOUND, BizCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception
    ) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, BizCode.NOT_SUPPORTED);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDatabaseError(DataAccessException exception) {
        log.error("Database operation failed", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, BizCode.SYSTEM_ERROR);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception) {
        log.debug("Async response already closed", exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled request exception", exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, BizCode.SYSTEM_ERROR);
    }

    private static ResponseEntity<Result<Void>> response(
            HttpStatus status,
            BizCode bizCode
    ) {
        return ResponseEntity.status(status).body(Result.fail(bizCode));
    }

    private static ResponseEntity<Result<Void>> response(
            HttpStatus status,
            BizCode bizCode,
            String detail
    ) {
        return ResponseEntity.status(status).body(Result.fail(bizCode, detail));
    }
}
