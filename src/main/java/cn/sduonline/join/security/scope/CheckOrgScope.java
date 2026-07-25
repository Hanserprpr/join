package cn.sduonline.join.security.scope;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验当前用户对指定板块、工作站或部门的数据范围。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckOrgScope {

    /** 本次操作需要的权限编码。权限和组织范围必须由同一条角色授权提供。 */
    PermissionCode permission();

    OrgType type();

    /** 动态 ID 的 SpEL，例如 #departmentId 或 #request.workstationId。 */
    String id();
}
