package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.UserRoleScope;
import cn.sduonline.join.data.dto.RoleAssignmentMemberVO;
import cn.sduonline.join.data.dto.UserSearchVO;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminRoleAssignmentMapper {

    /**
     * 查询可进入角色授权管理页面的管理员。辅助管理员没有可授予的更低级身份，
     * 因此不允许借助成员或用户联想接口枚举数据。
     */
    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            WHERE s.cas_id = #{casId}
              AND r.code IN (
                    'SYSTEM_ADMIN',
                    'BOARD_ADMIN',
                    'WORKSTATION_ADMIN',
                    'DEPARTMENT_ADMIN'
              )
            """)
    long countRoleAssignmentManager(@Param("casId") String casId);

    /**
     * 判断用户是否持有任一管理员角色。角色分配仅向管理员开放；具体可操作的
     * 角色等级和组织范围仍由 {@link #countGrantAuthority} 校验。
     */
    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            WHERE s.cas_id = #{casId}
              AND r.code IN (
                    'SYSTEM_ADMIN',
                    'BOARD_ADMIN',
                    'WORKSTATION_ADMIN',
                    'DEPARTMENT_ADMIN',
                    'DEPARTMENT_ASSISTANT'
              )
            """)
    long countAdminRole(@Param("casId") String casId);

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
     * 判断角色授权管理员的作用域是否覆盖查询目标。该校验只验证可见范围，
     * 不使用严格的角色等级比较，因此部门管理员可以查看本部门成员。
     */
    @Select("""
            SELECT COUNT(1)
            FROM user_role_scope s
            JOIN `role` r ON r.id = s.role_id
            WHERE s.cas_id = #{casId}
              AND r.code IN (
                    'SYSTEM_ADMIN',
                    'BOARD_ADMIN',
                    'WORKSTATION_ADMIN',
                    'DEPARTMENT_ADMIN'
              )
              AND (
                    s.scope_type = 'ALL'
                    OR (
                        #{targetScopeType} = 'BOARD'
                        AND s.scope_type = 'BOARD'
                        AND s.scope_id = #{targetScopeId}
                    )
                    OR (
                        #{targetScopeType} = 'WORKSTATION'
                        AND (
                            (s.scope_type = 'WORKSTATION'
                                AND s.scope_id = #{targetScopeId})
                            OR (
                                s.scope_type = 'BOARD'
                                AND EXISTS (
                                    SELECT 1 FROM workstation w
                                    WHERE w.id = #{targetScopeId}
                                      AND w.board_id = s.scope_id
                                )
                            )
                        )
                    )
                    OR (
                        #{targetScopeType} = 'DEPARTMENT'
                        AND (
                            (s.scope_type = 'DEPARTMENT'
                                AND s.scope_id = #{targetScopeId})
                            OR (
                                s.scope_type = 'WORKSTATION'
                                AND EXISTS (
                                    SELECT 1 FROM department d
                                    WHERE d.id = #{targetScopeId}
                                      AND d.workstation_id = s.scope_id
                                )
                            )
                            OR (
                                s.scope_type = 'BOARD'
                                AND EXISTS (
                                    SELECT 1
                                    FROM department d
                                    JOIN workstation w ON w.id = d.workstation_id
                                    WHERE d.id = #{targetScopeId}
                                      AND w.board_id = s.scope_id
                                )
                            )
                        )
                    )
              )
            """)
    long countScopeCoverage(
            @Param("casId") String casId,
            @Param("targetScopeType") String targetScopeType,
            @Param("targetScopeId") Long targetScopeId
    );

    /**
     * 查询与目标组织有关的有效成员授权：包括该节点及下级的直接授权和
     * 覆盖该节点的上级授权。响应保留原始作用域，使调用方能区分继承授权
     * 和直接授权。平台管理员（{@code SYSTEM_ADMIN}）不在成员列表中出现。
     * 结果按角色等级从高到低排序，同一等级内按姓名、学号排列。
     */
    @Select("""
            SELECT s.id AS id,
                   s.cas_id AS cas_id,
                   u.name AS name,
                   r.code AS role_code,
                   r.name AS role_name,
                   s.scope_type AS scope_type,
                   s.scope_id AS scope_id,
                   CASE s.scope_type
                     WHEN 'ALL' THEN '全平台'
                     WHEN 'BOARD' THEN scope_board.name
                     WHEN 'WORKSTATION' THEN scope_workstation.name
                     WHEN 'DEPARTMENT' THEN scope_department.name
                   END AS scope_name,
                   CASE s.scope_type
                     WHEN 'BOARD' THEN scope_board.id
                     WHEN 'WORKSTATION' THEN workstation_board.id
                     WHEN 'DEPARTMENT' THEN department_board.id
                   END AS board_id,
                   CASE s.scope_type
                     WHEN 'BOARD' THEN scope_board.name
                     WHEN 'WORKSTATION' THEN workstation_board.name
                     WHEN 'DEPARTMENT' THEN department_board.name
                   END AS board_name,
                   CASE s.scope_type
                     WHEN 'WORKSTATION' THEN scope_workstation.id
                     WHEN 'DEPARTMENT' THEN department_workstation.id
                   END AS workstation_id,
                   CASE s.scope_type
                     WHEN 'WORKSTATION' THEN scope_workstation.name
                     WHEN 'DEPARTMENT' THEN department_workstation.name
                   END AS workstation_name,
                   CASE s.scope_type
                     WHEN 'DEPARTMENT' THEN scope_department.id
                   END AS department_id,
                   CASE s.scope_type
                     WHEN 'DEPARTMENT' THEN scope_department.name
                   END AS department_name
            FROM user_role_scope s
            JOIN `user` u ON u.cas_id = s.cas_id
            JOIN `role` r ON r.id = s.role_id
            LEFT JOIN board scope_board
              ON s.scope_type = 'BOARD' AND scope_board.id = s.scope_id
            LEFT JOIN workstation scope_workstation
              ON s.scope_type = 'WORKSTATION' AND scope_workstation.id = s.scope_id
            LEFT JOIN board workstation_board
              ON workstation_board.id = scope_workstation.board_id
            LEFT JOIN department scope_department
              ON s.scope_type = 'DEPARTMENT' AND scope_department.id = s.scope_id
            LEFT JOIN workstation department_workstation
              ON department_workstation.id = scope_department.workstation_id
            LEFT JOIN board department_board
              ON department_board.id = department_workstation.board_id
            WHERE r.code <> 'SYSTEM_ADMIN'
              AND (
                    s.scope_type = 'ALL'
                    OR (
                         #{targetScopeType} = 'BOARD'
                         AND (
                             (s.scope_type = 'BOARD' AND s.scope_id = #{targetScopeId})
                             OR (
                                 s.scope_type = 'WORKSTATION'
                                 AND EXISTS (
                                     SELECT 1 FROM workstation w
                                     WHERE w.id = s.scope_id
                                       AND w.board_id = #{targetScopeId}
                                 )
                             )
                             OR (
                                 s.scope_type = 'DEPARTMENT'
                                 AND EXISTS (
                                     SELECT 1
                                     FROM department d
                                     JOIN workstation w ON w.id = d.workstation_id
                                     WHERE d.id = s.scope_id
                                       AND w.board_id = #{targetScopeId}
                                 )
                             )
                         )
                    )
                    OR (
                         #{targetScopeType} = 'WORKSTATION'
                         AND (
                             (s.scope_type = 'WORKSTATION'
                                 AND s.scope_id = #{targetScopeId})
                             OR (
                                 s.scope_type = 'BOARD'
                                 AND EXISTS (
                                     SELECT 1 FROM workstation w
                                     WHERE w.id = #{targetScopeId}
                                       AND w.board_id = s.scope_id
                                 )
                             )
                             OR (
                                 s.scope_type = 'DEPARTMENT'
                                 AND EXISTS (
                                     SELECT 1 FROM department d
                                     WHERE d.id = s.scope_id
                                       AND d.workstation_id = #{targetScopeId}
                                 )
                             )
                         )
                    )
                    OR (
                         #{targetScopeType} = 'DEPARTMENT'
                         AND (
                             (s.scope_type = 'DEPARTMENT'
                                 AND s.scope_id = #{targetScopeId})
                             OR (
                                 s.scope_type = 'WORKSTATION'
                                 AND EXISTS (
                                     SELECT 1 FROM department d
                                     WHERE d.id = #{targetScopeId}
                                       AND d.workstation_id = s.scope_id
                                 )
                             )
                             OR (
                                 s.scope_type = 'BOARD'
                                 AND EXISTS (
                                     SELECT 1
                                     FROM department d
                                     JOIN workstation w ON w.id = d.workstation_id
                                     WHERE d.id = #{targetScopeId}
                                       AND w.board_id = s.scope_id
                                 )
                             )
                         )
                    )
              )
            ORDER BY CASE r.code
                       WHEN 'BOARD_ADMIN' THEN 40
                       WHEN 'WORKSTATION_ADMIN' THEN 30
                       WHEN 'DEPARTMENT_ADMIN' THEN 20
                       WHEN 'DEPARTMENT_ASSISTANT' THEN 10
                       ELSE 0
                     END DESC,
                     u.name ASC, u.cas_id ASC, r.code ASC,
                     s.scope_type ASC, s.scope_id ASC, s.id ASC
            """)
    List<RoleAssignmentMemberVO> selectMembersByScope(
            @Param("targetScopeType") String targetScopeType,
            @Param("targetScopeId") Long targetScopeId
    );

    /**
     * 按学号包含匹配查询角色授权候选人。调用方传入的内容已经按 LIKE 转义，
     * 由服务层要求至少六位输入，且固定上限避免输入过程枚举大量用户。
     */
    @Select("""
            SELECT cas_id AS cas_id, name
            FROM `user`
            WHERE cas_id LIKE CONCAT('%', #{casIdKeyword}, '%') ESCAPE '!'
            ORDER BY cas_id ASC
            LIMIT 20
            """)
    List<UserSearchVO> selectUsersByCasIdKeyword(
            @Param("casIdKeyword") String casIdKeyword
    );

    /**
     * 查找一条级别高于待授予角色、且数据范围覆盖目标组织的操作者授权。
     * 授予资格只看角色等级和数据范围，不校验 admin:role:assign 等权限码，
     * 因此这两个权限码不参与本接口的判定。
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
