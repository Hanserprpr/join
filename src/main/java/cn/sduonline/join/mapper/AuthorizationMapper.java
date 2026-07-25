package cn.sduonline.join.mapper;

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
}
