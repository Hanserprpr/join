package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.Board;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentPoster;
import cn.sduonline.join.data.po.Workstation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import java.util.List;

@Mapper
public interface AdminOrganizationMapper {

    @Select("SELECT COUNT(1) FROM board WHERE id = #{id} AND enabled = 1")
    long countEnabledBoard(@Param("id") Long id);

    @Select("SELECT COUNT(1) FROM workstation WHERE id = #{id} AND enabled = 1")
    long countEnabledWorkstation(@Param("id") Long id);

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

    @Select("""
            SELECT id, board_id, name, sort_order, enabled
            FROM workstation
            WHERE id = #{id}
              AND enabled = 1
            """)
    Workstation selectEnabledWorkstationById(@Param("id") Long id);

    @Select("""
            SELECT id, workstation_id, name, campus, sort_order, enabled
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

    @Select("""
            SELECT id, workstation_id, name, campus, introduction,
                   achievements, recruitment_requirements, contact,
                   recruitment_group, sort_order, enabled
            FROM department
            WHERE id = #{id}
            """)
    Department selectDepartmentById(@Param("id") Long id);

    @Update("""
            UPDATE department
            SET campus = #{campus},
                introduction = #{introduction},
                achievements = #{achievements},
                recruitment_requirements = #{recruitmentRequirements},
                contact = #{contact},
                recruitment_group = #{recruitmentGroup}
            WHERE id = #{id}
            """)
    int updateDepartmentDetail(Department department);

    @Select("""
            SELECT id, department_id, url, sort_order
            FROM department_poster
            WHERE department_id = #{departmentId}
            ORDER BY sort_order ASC, id ASC
            """)
    List<DepartmentPoster> selectDepartmentPosters(
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
}
