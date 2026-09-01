package cn.sduonline.join.mapper;

import cn.sduonline.join.data.dto.DashboardCollegeDistributionVO;
import cn.sduonline.join.data.dto.DashboardDepartmentApplicationVO;
import cn.sduonline.join.data.dto.DashboardInterviewAggregateRow;
import cn.sduonline.join.data.dto.DashboardScopeDepartmentRow;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DashboardStatisticsMapper {

    @Select("""
            SELECT b.id AS scope_id, b.name AS scope_name,
                   d.id AS department_id, d.name AS department_name,
                   w.id AS workstation_id, w.name AS workstation_name
            FROM board b
            LEFT JOIN workstation w
              ON w.board_id = b.id AND w.enabled = 1
            LEFT JOIN department d
              ON d.workstation_id = w.id AND d.enabled = 1
            WHERE b.id = #{scopeId} AND b.enabled = 1
            ORDER BY d.sort_order ASC, d.id ASC
            """)
    List<DashboardScopeDepartmentRow> selectBoardScope(
            @Param("scopeId") Long scopeId
    );

    @Select("""
            SELECT w.id AS scope_id, w.name AS scope_name,
                   d.id AS department_id, d.name AS department_name,
                   w.id AS workstation_id, w.name AS workstation_name
            FROM workstation w
            JOIN board b ON b.id = w.board_id AND b.enabled = 1
            LEFT JOIN department d
              ON d.workstation_id = w.id AND d.enabled = 1
            WHERE w.id = #{scopeId} AND w.enabled = 1
            ORDER BY d.sort_order ASC, d.id ASC
            """)
    List<DashboardScopeDepartmentRow> selectWorkstationScope(
            @Param("scopeId") Long scopeId
    );

    @Select("""
            SELECT d.id AS scope_id, d.name AS scope_name,
                   d.id AS department_id, d.name AS department_name,
                   w.id AS workstation_id, w.name AS workstation_name
            FROM department d
            JOIN workstation w
              ON w.id = d.workstation_id AND w.enabled = 1
            JOIN board b ON b.id = w.board_id AND b.enabled = 1
            WHERE d.id = #{scopeId} AND d.enabled = 1
            """)
    List<DashboardScopeDepartmentRow> selectDepartmentScope(
            @Param("scopeId") Long scopeId
    );

    @Select("""
            <script>
            SELECT d.id AS department_id, d.name AS department_name,
                   w.id AS workstation_id, w.name AS workstation_name,
                   COUNT(a.id) AS application_count
            FROM department d
            JOIN workstation w ON w.id = d.workstation_id
            LEFT JOIN department_application a ON a.department_id = d.id
            WHERE d.id IN
            <foreach item="departmentId" collection="departmentIds"
                     open="(" separator="," close=")">
                #{departmentId}
            </foreach>
            GROUP BY d.id, d.name, w.id, w.name
            ORDER BY application_count DESC, d.id ASC
            </script>
            """)
    List<DashboardDepartmentApplicationVO> selectApplicationCountsByDepartment(
            @Param("departmentIds") List<Long> departmentIds
    );

    @Select("""
            <script>
            SELECT COALESCE(NULLIF(TRIM(u.college), ''), '未知学院') AS college,
                   COUNT(*) AS application_count
            FROM department_application a
            JOIN `user` u ON u.cas_id = a.cas_id
            WHERE a.department_id IN
            <foreach item="departmentId" collection="departmentIds"
                     open="(" separator="," close=")">
                #{departmentId}
            </foreach>
            GROUP BY COALESCE(NULLIF(TRIM(u.college), ''), '未知学院')
            ORDER BY application_count DESC, college ASC
            </script>
            """)
    List<DashboardCollegeDistributionVO> selectApplicationCountsByCollege(
            @Param("departmentIds") List<Long> departmentIds
    );

    @Select("""
            SELECT
              COUNT(DISTINCT CASE WHEN s.status = 'PUBLISHED' THEN s.id END)
                AS published_session_count,
              COUNT(DISTINCT CASE
                WHEN s.status IN ('PUBLISHED', 'ENDED') THEN s.id END)
                AS operational_session_count,
              COUNT(DISTINCT CASE
                WHEN s.status IN ('PUBLISHED', 'ENDED')
                 AND c.requires_recheck_in = FALSE
                 AND own_interview.ended_at IS NULL
                 AND own_active.interview_id IS NULL
                 AND other_active.interview_id IS NULL
                THEN c.id END) AS waiting_count
            FROM department_interview_session s
            LEFT JOIN department_check_in c ON c.session_id = s.id
            LEFT JOIN department_interview own_interview
              ON own_interview.check_in_id = c.id
            LEFT JOIN department_interview_active own_active
              ON own_active.interview_id = own_interview.id
            LEFT JOIN department_interview_active other_active
              ON other_active.candidate_cas_id = c.cas_id
             AND other_active.interview_id <> COALESCE(own_interview.id, -1)
            WHERE s.department_id = #{departmentId}
            """)
    DashboardInterviewAggregateRow selectInterviewAggregate(
            @Param("departmentId") Long departmentId
    );

    @Select("""
            SELECT id, department_id, name, starts_at, ends_at, location,
                   check_in_limit, qr_check_in_enabled, qr_code_ttl_seconds,
                   status, published_at, ended_at
            FROM department_interview_session
            WHERE department_id = #{departmentId}
              AND status = 'PUBLISHED'
            ORDER BY starts_at ASC, id ASC
            LIMIT 1
            """)
    DepartmentInterviewSession selectHighlightedSession(
            @Param("departmentId") Long departmentId
    );
}
