package cn.sduonline.join.security.scope;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.service.AuthorizationService;
import java.lang.reflect.Method;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class OrgScopeAspect {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAMETER_NAMES =
            new DefaultParameterNameDiscoverer();

    private final AuthorizationService authorizationService;

    @Around("@annotation(scope)")
    public Object checkOrgScope(
            ProceedingJoinPoint joinPoint,
            CheckOrgScope scope
    ) throws Throwable {
        return check(
                joinPoint, scope.permission().code(), scope.type(), scope.id()
        );
    }

    @Around("@annotation(permission)")
    public Object checkBoard(
            ProceedingJoinPoint joinPoint,
            BoardPermission permission
    ) throws Throwable {
        return check(
                joinPoint, permission.value().code(), OrgType.BOARD, permission.id()
        );
    }

    @Around("@annotation(permission)")
    public Object checkWorkstation(
            ProceedingJoinPoint joinPoint,
            WorkstationPermission permission
    ) throws Throwable {
        return check(
                joinPoint, permission.value().code(), OrgType.WORKSTATION, permission.id()
        );
    }

    @Around("@annotation(permission)")
    public Object checkDepartment(
            ProceedingJoinPoint joinPoint,
            DepartmentPermission permission
    ) throws Throwable {
        return check(
                joinPoint, permission.value().code(), OrgType.DEPARTMENT, permission.id()
        );
    }

    private Object check(
            ProceedingJoinPoint joinPoint,
            String permission,
            OrgType type,
            String idExpression
    ) throws Throwable {
        StpUtil.checkLogin();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                null, method, joinPoint.getArgs(), PARAMETER_NAMES
        );
        Object value = PARSER.parseExpression(idExpression).getValue(context);
        Long targetId = toLong(value);
        String casId = StpUtil.getLoginIdAsString();

        if (!authorizationService.canAccessWithPermission(
                casId, permission, type, targetId
        )) {
            throw new NotPermissionException(
                    permission,
                    StpUtil.TYPE
            );
        }
        return joinPoint.proceed();
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                // 统一在下面按非法参数处理。
            }
        }
        throw new IllegalArgumentException("无法从 Scope 注解表达式解析有效的组织 ID");
    }
}
