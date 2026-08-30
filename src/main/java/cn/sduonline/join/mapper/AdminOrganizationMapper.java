package cn.sduonline.join.mapper;

import cn.sduonline.join.data.dto.OrganizationTreeRow;
import cn.sduonline.join.data.po.Board;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentAchievement;
import cn.sduonline.join.data.po.DepartmentPoster;
import cn.sduonline.join.data.po.Workstation;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminOrganizationMapper {

    @Select("SELECT COUNT(1) FROM board WHERE id = #{id} AND enabled = 1")
    long countEnabledBoard(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM workstation WHERE id = #{id} AND enabled = 1")
    long countEnabledWorkstation(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM board WHERE id = #{id}")
    long countBoard(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM workstation WHERE id = #{id}")
    long countWorkstation(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM department WHERE id = #{id}")
    long countDepartment(@Param("id") Long id);

    @Insert("""
            INSERT INTO board (name, sort_order, enabled)
            VALUES (#{name}, #{sortOrder}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertBoard(Board board);

    @Select("""
            SELECT id, name, sort_order, enabled
            FROM board
            WHERE enabled = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<Board> selectEnabledBoards();

    @Select("""
            SELECT id, board_id, name, sort_order, enabled
            FROM workstation
            WHERE board_id = #{boardId}
              AND enabled = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<Workstation> selectEnabledWorkstationsByBoard(
            @Param("boardId") Long boardId
    );

    /**
     * 一次性取回全部启用组织树，服务层会按 ID 聚合成嵌套响应。
     */
    @Select("""
            SELECT b.id AS board_id,
                   b.name AS board_name,
                   w.id AS workstation_id,
                   w.name AS workstation_name,
                   d.id AS department_id,
                   d.name AS department_name,
                   d.campus AS department_campus,
                   d.asset_id AS department_asset_id,
                   d.introduction AS department_introduction
            FROM board b
            LEFT JOIN workstation w
              ON w.board_id = b.id
             AND w.enabled = 1
            LEFT JOIN department d
              ON d.workstation_id = w.id
             AND d.enabled = 1
            WHERE b.enabled = 1
            ORDER BY b.sort_order ASC, b.id ASC,
                     w.sort_order ASC, w.id ASC,
                     d.sort_order ASC, d.id ASC
            """)
    List<OrganizationTreeRow> selectEnabledOrganizationTree();

    @Select("""
            SELECT id, board_id, name, sort_order, enabled
            FROM workstation
            WHERE id = #{id}
              AND enabled = 1
            """)
    Workstation selectEnabledWorkstationById(@Param("id") Long id);

    @Select("""
            SELECT id, workstation_id, name, campus, asset_id,
                   sort_order, enabled
            FROM department
            WHERE workstation_id = #{workstationId}
              AND enabled = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<Department> selectEnabledDepartmentsByWorkstation(
            @Param("workstationId") Long workstationId
    );

    @Insert("""
            INSERT INTO workstation (board_id, name, sort_order, enabled)
            VALUES (#{boardId}, #{name}, #{sortOrder}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWorkstation(Workstation workstation);

    @Insert("""
            INSERT INTO department
                (workstation_id, name, sort_order, enabled)
            VALUES
                (#{workstationId}, #{name}, #{sortOrder}, #{enabled})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDepartment(Department department);

    @Update("UPDATE board SET name = #{name} WHERE id = #{id}")
    int updateBoardName(
            @Param("id") Long id,
            @Param("name") String name
    );

    @Update("UPDATE workstation SET name = #{name} WHERE id = #{id}")
    int updateWorkstationName(
            @Param("id") Long id,
            @Param("name") String name
    );

    @Update("UPDATE department SET name = #{name} WHERE id = #{id}")
    int updateDepartmentName(
            @Param("id") Long id,
            @Param("name") String name
    );

    @Select("""
            SELECT id, workstation_id, name, campus, introduction,
                   recruitment_requirements, contact,
                   recruitment_group, sort_order, enabled
            FROM department
            WHERE id = #{id}
              AND enabled = 1
            """)
    Department selectDepartmentById(@Param("id") Long id);

    @Select("""
            SELECT id, workstation_id, name, campus, introduction,
                   recruitment_requirements, contact,
                   recruitment_group, sort_order, enabled
            FROM department
            WHERE id = #{id}
              AND enabled = 1
            FOR UPDATE
            """)
    Department selectDepartmentByIdForUpdate(@Param("id") Long id);

    @Update("""
            UPDATE department
            SET campus = #{campus},
                introduction = #{introduction},
                recruitment_requirements = #{recruitmentRequirements},
                contact = #{contact},
                recruitment_group = #{recruitmentGroup}
            WHERE id = #{id}
            """)
    int updateDepartmentDetail(Department department);

    @Select("""
            SELECT id, department_id, title, content, sort_order
            FROM department_achievement
            WHERE department_id = #{departmentId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<DepartmentAchievement> selectDepartmentAchievements(
            @Param("departmentId") Long departmentId
    );

    @Delete("DELETE FROM department_achievement WHERE department_id = #{departmentId}")
    int deleteDepartmentAchievements(@Param("departmentId") Long departmentId);

    @Insert("""
            INSERT INTO department_achievement
                (department_id, title, content, sort_order)
            VALUES
                (#{departmentId}, #{title}, #{content}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDepartmentAchievement(DepartmentAchievement achievement);

    @Select("""
            SELECT id, department_id, url, sort_order
            FROM department_poster
            WHERE department_id = #{departmentId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<DepartmentPoster> selectDepartmentPosters(
            @Param("departmentId") Long departmentId
    );

    @Select("""
            SELECT id, department_id, url, sort_order
            FROM department_poster
            WHERE department_id = #{departmentId}
            ORDER BY id ASC
            FOR UPDATE
            """)
    List<DepartmentPoster> selectDepartmentPostersForUpdate(
            @Param("departmentId") Long departmentId
    );

    @Delete("DELETE FROM department_poster WHERE department_id = #{departmentId}")
    int deleteDepartmentPosters(@Param("departmentId") Long departmentId);

    @Insert("""
            INSERT INTO department_poster (department_id, url, sort_order)
            VALUES (#{departmentId}, #{url}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertDepartmentPoster(DepartmentPoster poster);

    @Update("""
            UPDATE department_poster
            SET sort_order = #{sortOrder}
            WHERE department_id = #{departmentId}
              AND id = #{posterId}
            """)
    int updateDepartmentPosterSortOrder(
            @Param("departmentId") Long departmentId,
            @Param("posterId") Long posterId,
            @Param("sortOrder") Integer sortOrder
    );

    @Select("SELECT id FROM workstation WHERE board_id = #{boardId}")
    List<Long> selectWorkstationIdsByBoard(@Param("boardId") Long boardId);

    @Select("SELECT id FROM department WHERE workstation_id = #{workstationId}")
    List<Long> selectDepartmentIdsByWorkstation(@Param("workstationId") Long workstationId);

    @Delete("DELETE FROM department_interview WHERE department_id = #{departmentId}")
    int deleteInterviewsByDepartment(@Param("departmentId") Long departmentId);

    @Delete("DELETE FROM department_interview_carryover WHERE department_id = #{departmentId}")
    int deleteInterviewCarryoversByDepartment(@Param("departmentId") Long departmentId);

    @Delete("""
            DELETE s FROM department_check_in_sequence s
            JOIN department_interview_session sess ON sess.id = s.session_id
            WHERE sess.department_id = #{departmentId}
            """)
    int deleteCheckInSequencesByDepartment(@Param("departmentId") Long departmentId);

    @Delete("DELETE FROM department_check_in WHERE department_id = #{departmentId}")
    int deleteCheckInsByDepartment(@Param("departmentId") Long departmentId);

    @Delete("DELETE FROM department_interview_room WHERE department_id = #{departmentId}")
    int deleteInterviewRoomsByDepartment(@Param("departmentId") Long departmentId);

    @Delete("""
            DELETE o FROM admission_email_outbox o
            JOIN department_application a ON a.id = o.application_id
            WHERE a.department_id = #{departmentId}
            """)
    int deleteAdmissionEmailOutboxByDepartment(@Param("departmentId") Long departmentId);

    @Delete("DELETE FROM department_application WHERE department_id = #{departmentId}")
    int deleteApplicationsByDepartment(@Param("departmentId") Long departmentId);

    @Delete("DELETE FROM department_interview_session WHERE department_id = #{departmentId}")
    int deleteInterviewSessionsByDepartment(@Param("departmentId") Long departmentId);

    @Delete("""
            DELETE FROM user_role_scope
            WHERE scope_type = #{scopeType} AND scope_id = #{scopeId}
            """)
    int deleteRoleScopesByScope(
            @Param("scopeType") String scopeType,
            @Param("scopeId") Long scopeId
    );

    @Delete("DELETE FROM department WHERE id = #{id}")
    int deleteDepartmentById(@Param("id") Long id);

    @Delete("DELETE FROM workstation WHERE id = #{id}")
    int deleteWorkstationById(@Param("id") Long id);

    @Delete("DELETE FROM board WHERE id = #{id}")
    int deleteBoardById(@Param("id") Long id);
}
