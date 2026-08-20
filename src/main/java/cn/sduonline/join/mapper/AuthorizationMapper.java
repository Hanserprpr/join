package cn.sduonline.join.mapper;

import cn.sduonline.join.data.dto.DepartmentRoleAccess;
import cn.sduonline.join.data.dto.PermissionDepartmentAccess;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthorizationMapper {

    @Select("""
            SELECT DISTINCT r.code
            FROM user_role_scope urs
            JOIN `role` r ON r.id = urs.role_id
            WHERE urs.cas_id = #{casId}
            """)
    List<String> selectRoleCodes(@Param("casId") String casId);

    @Select("""
            SELECT DISTINCT p.code
            FROM user_role_scope urs
            JOIN role_permission rp ON rp.role_id = urs.role_id
            JOIN `permission` p ON p.id = rp.permission_id
            WHERE urs.cas_id = #{casId}
            """)
    List<String> selectPermissionCodes(@Param("casId") String casId);

    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            LEFT JOIN role_permission rp ON rp.role_id = r.id
            LEFT JOIN permission p ON p.id = rp.permission_id
            WHERE s.cas_id = #{casId}
              AND (r.code = 'SYSTEM_ADMIN' OR p.code = #{permission})
              AND EXISTS (
                    SELECT 1 FROM board b
                    WHERE b.id = #{boardId} AND b.enabled = 1
              )
              AND (
                    s.scope_type = 'ALL'
                    OR (s.scope_type = 'BOARD' AND s.scope_id = #{boardId})
              )
            """)
    long countBoardPermissionAccess(
            @Param("casId") String casId,
            @Param("permission") String permission,
            @Param("boardId") Long boardId
    );

    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            LEFT JOIN role_permission rp ON rp.role_id = r.id
            LEFT JOIN permission p ON p.id = rp.permission_id
            WHERE s.cas_id = #{casId}
              AND (r.code = 'SYSTEM_ADMIN' OR p.code = #{permission})
              AND EXISTS (
                    SELECT 1 FROM workstation w
                    WHERE w.id = #{workstationId} AND w.enabled = 1
              )
              AND (
                    s.scope_type = 'ALL'
                    OR (s.scope_type = 'WORKSTATION' AND s.scope_id = #{workstationId})
                    OR (
                        s.scope_type = 'BOARD'
                        AND EXISTS (
                            SELECT 1 FROM workstation w
                            WHERE w.id = #{workstationId}
                              AND w.board_id = s.scope_id
                        )
                    )
              )
            """)
    long countWorkstationPermissionAccess(
            @Param("casId") String casId,
            @Param("permission") String permission,
            @Param("workstationId") Long workstationId
    );

    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            LEFT JOIN role_permission rp ON rp.role_id = r.id
            LEFT JOIN permission p ON p.id = rp.permission_id
            WHERE s.cas_id = #{casId}
              AND (r.code = 'SYSTEM_ADMIN' OR p.code = #{permission})
              AND EXISTS (
                    SELECT 1 FROM department dt
                    WHERE dt.id = #{departmentId} AND dt.enabled = 1
              )
              AND (
                    s.scope_type = 'ALL'
                    OR (s.scope_type = 'DEPARTMENT' AND s.scope_id = #{departmentId})
                    OR (
                        s.scope_type = 'WORKSTATION'
                        AND EXISTS (
                            SELECT 1 FROM department d
                            WHERE d.id = #{departmentId}
                              AND d.workstation_id = s.scope_id
                        )
                    )
                    OR (
                        s.scope_type = 'BOARD'
                        AND EXISTS (
                            SELECT 1
                            FROM department d
                            JOIN workstation w ON w.id = d.workstation_id
                            WHERE d.id = #{departmentId}
                              AND w.board_id = s.scope_id
                        )
                    )
              )
            """)
    long countDepartmentPermissionAccess(
            @Param("casId") String casId,
            @Param("permission") String permission,
            @Param("departmentId") Long departmentId
    );

    /**
     * 查询该用户按权限可管理的启用部门。
     * 作用域按 ALL → BOARD → WORKSTATION → DEPARTMENT 逐级展开；
     * SYSTEM_ADMIN 无角色权限关联，权限码统一映射为 {@code *}。
     *
     * @param casId 学号
     * @return 权限编码与部门 ID 的去重组合
     */
    @Select("""
            SELECT DISTINCT COALESCE(p.code, '*') AS permissionCode, d.id AS departmentId
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            LEFT JOIN role_permission rp ON rp.role_id = r.id
            LEFT JOIN `permission` p ON p.id = rp.permission_id
            JOIN department d
            WHERE s.cas_id = #{casId}
              AND (r.code = 'SYSTEM_ADMIN' OR p.code IS NOT NULL)
              AND d.enabled = 1
              AND (
                    s.scope_type = 'ALL'
                    OR (s.scope_type = 'DEPARTMENT' AND d.id = s.scope_id)
                    OR (s.scope_type = 'WORKSTATION' AND d.workstation_id = s.scope_id)
                    OR (s.scope_type = 'BOARD' AND EXISTS (
                        SELECT 1 FROM workstation w
                        WHERE w.id = d.workstation_id AND w.board_id = s.scope_id
                    ))
              )
            ORDER BY permissionCode, departmentId
            """)
    List<PermissionDepartmentAccess> selectScopedDepartmentAccess(
            @Param("casId") String casId
    );

    /**
     * 查询该用户在各部门担任的角色及权限。
     * 作用域按 ALL → BOARD → WORKSTATION → DEPARTMENT 逐级展开到部门；
     * SYSTEM_ADMIN 无角色权限关联，权限码统一映射为 {@code *}。
     * 其余角色必须命中 role_permission，否则整行剔除，避免没有配置任何权限的
     * 角色被 COALESCE 误报成 {@code *}。
     *
     * @param casId 学号
     * @return 部门、角色与权限的去重组合
     */
    @Select("""
            SELECT DISTINCT
                   d.id AS departmentId,
                   d.name AS departmentName,
                   r.code AS roleCode,
                   r.name AS roleName,
                   s.scope_type AS scopeType,
                   COALESCE(p.code, '*') AS permissionCode
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            LEFT JOIN role_permission rp ON rp.role_id = r.id
            LEFT JOIN `permission` p ON p.id = rp.permission_id
            JOIN department d
            WHERE s.cas_id = #{casId}
              AND (r.code = 'SYSTEM_ADMIN' OR p.code IS NOT NULL)
              AND d.enabled = 1
              AND (
                    s.scope_type = 'ALL'
                    OR (s.scope_type = 'DEPARTMENT' AND d.id = s.scope_id)
                    OR (s.scope_type = 'WORKSTATION' AND d.workstation_id = s.scope_id)
                    OR (s.scope_type = 'BOARD' AND EXISTS (
                        SELECT 1 FROM workstation w
                        WHERE w.id = d.workstation_id AND w.board_id = s.scope_id
                    ))
              )
            ORDER BY d.id, r.code, permissionCode
            """)
    List<DepartmentRoleAccess> selectDepartmentRoleAccess(
            @Param("casId") String casId
    );
}
