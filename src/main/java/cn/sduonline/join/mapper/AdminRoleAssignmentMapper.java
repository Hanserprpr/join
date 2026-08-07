package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.UserRoleScope;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRoleAssignmentMapper {

    @Select("SELECT COUNT(1) FROM `user` WHERE cas_id = #{casId}")
    long countUser(@Param("casId") String casId);

    @Select("SELECT id FROM `role` WHERE code = #{code}")
    Long selectRoleId(@Param("code") String code);

    @Select("SELECT COUNT(1) FROM board WHERE id = #{id} AND enabled = 1")
    long countEnabledBoard(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM workstation WHERE id = #{id} AND enabled = 1")
    long countEnabledWorkstation(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM department WHERE id = #{id} AND enabled = 1")
    long countEnabledDepartment(@Param("id") Long id);

    /**
     * 查找一条级别高于待授予角色、且数据范围覆盖目标组织的操作者授权。
     */
    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            WHERE s.cas_id = #{operatorCasId}
              AND CASE r.code
                    WHEN 'SYSTEM_ADMIN' THEN 50
                    WHEN 'BOARD_ADMIN' THEN 40
                    WHEN 'WORKSTATION_ADMIN' THEN 30
                    WHEN 'DEPARTMENT_ADMIN' THEN 20
                    WHEN 'DEPARTMENT_ASSISTANT' THEN 10
                    ELSE 0
                  END > #{targetRoleLevel}
              AND (
                    s.scope_type = 'ALL'
                    OR (
                        s.scope_type = #{targetScopeType}
                        AND s.scope_id = #{targetScopeId}
                    )
                    OR (
                        s.scope_type = 'BOARD'
                        AND #{targetScopeType} = 'WORKSTATION'
                        AND EXISTS (
                            SELECT 1 FROM workstation w
                            WHERE w.id = #{targetScopeId}
                              AND w.board_id = s.scope_id
                        )
                    )
                    OR (
                        s.scope_type = 'BOARD'
                        AND #{targetScopeType} = 'DEPARTMENT'
                        AND EXISTS (
                            SELECT 1
                            FROM department d
                            JOIN workstation w ON w.id = d.workstation_id
                            WHERE d.id = #{targetScopeId}
                              AND w.board_id = s.scope_id
                        )
                    )
                    OR (
                        s.scope_type = 'WORKSTATION'
                        AND #{targetScopeType} = 'DEPARTMENT'
                        AND EXISTS (
                            SELECT 1 FROM department d
                            WHERE d.id = #{targetScopeId}
                              AND d.workstation_id = s.scope_id
                        )
                    )
              )
            """)
    long countGrantAuthority(
            @Param("operatorCasId") String operatorCasId,
            @Param("targetRoleLevel") int targetRoleLevel,
            @Param("targetScopeType") String targetScopeType,
            @Param("targetScopeId") Long targetScopeId
    );

    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope
            WHERE cas_id = #{casId}
              AND role_id = #{roleId}
              AND scope_type = #{scopeType}
              AND scope_id = #{scopeId}
            """)
    long countAssignment(
            @Param("casId") String casId,
            @Param("roleId") Long roleId,
            @Param("scopeType") String scopeType,
            @Param("scopeId") Long scopeId
    );

    @Insert("""
            INSERT INTO user_role_scope
                (cas_id, role_id, scope_type, scope_id)
            VALUES
                (#{casId}, #{roleId}, #{scopeType}, #{scopeId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAssignment(UserRoleScope assignment);

    @Delete("""
            DELETE FROM user_role_scope
            WHERE cas_id = #{casId}
              AND role_id = #{roleId}
              AND scope_type = #{scopeType}
              AND scope_id = #{scopeId}
            """)
    int deleteAssignment(
            @Param("casId") String casId,
            @Param("roleId") Long roleId,
            @Param("scopeType") String scopeType,
            @Param("scopeId") Long scopeId
    );
}
