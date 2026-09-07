package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentCheckIn;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentCheckInMapper {

    @Select("""
            SELECT COUNT(1)
            FROM department_interview
            WHERE application_id = #{applicationId}
            """)
    int countInterviewsByApplication(
            @Param("applicationId") Long applicationId
    );

    /**
     * 统计同一报名在本部门其他已发布场次的签到数。
     * 用于保证同一部门同时只排一条队，避免跨校区双签占号。
     */
    @Select("""
            SELECT COUNT(1)
            FROM department_check_in c
            JOIN department_interview_session s
              ON s.id = c.session_id AND s.status = 'PUBLISHED'
            WHERE c.department_id = #{departmentId}
              AND c.application_id = #{applicationId}
              AND c.session_id <> #{sessionId}
            """)
    int countOtherSessionCheckIns(
            @Param("departmentId") Long departmentId,
            @Param("applicationId") Long applicationId,
            @Param("sessionId") Long sessionId
    );

    @Select("""
            SELECT id, department_id, application_id, cas_id,
                   session_id, checked_in_at, queue_number, queue_order,
                   pass_count, priority, requires_recheck_in
            FROM department_check_in
            WHERE session_id = #{sessionId}
              AND application_id = #{applicationId}
            FOR UPDATE
            """)
    DepartmentCheckIn selectBySessionAndApplication(
            @Param("sessionId") Long sessionId,
            @Param("applicationId") Long applicationId
    );

    @Select("""
            SELECT c.id, c.department_id, c.application_id, c.cas_id,
                   c.session_id, c.checked_in_at, c.queue_number, c.queue_order,
                   c.pass_count, c.priority, c.requires_recheck_in
            FROM department_check_in c
            WHERE c.department_id = #{departmentId}
              AND c.session_id = #{sessionId}
              AND c.cas_id = #{casId}
            FOR UPDATE
            """)
    DepartmentCheckIn selectCurrentByDepartmentAndUserForUpdate(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId,
            @Param("casId") String casId
    );

    @Select("""
            SELECT COUNT(1)
            FROM department_interview
            WHERE check_in_id = #{checkInId}
            """)
    int countInterviewsByCheckIn(@Param("checkInId") Long checkInId);

    @Delete("""
            DELETE FROM department_check_in
            WHERE id = #{checkInId}
              AND department_id = #{departmentId}
              AND cas_id = #{casId}
            """)
    int deleteOwnedCheckIn(
            @Param("checkInId") Long checkInId,
            @Param("departmentId") Long departmentId,
            @Param("casId") String casId
    );

    @Insert("""
            INSERT INTO department_check_in
                (department_id, session_id, application_id, cas_id,
                 checked_in_at, queue_number, queue_order, pass_count,
                 priority, requires_recheck_in)
            VALUES
                (#{departmentId}, #{sessionId}, #{applicationId}, #{casId},
                 #{checkedInAt}, #{queueNumber}, #{queueOrder}, #{passCount},
                 #{priority}, #{requiresRecheckIn})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DepartmentCheckIn checkIn);

    @Update("""
            UPDATE department_check_in
            SET checked_in_at = #{checkedInAt},
                queue_number = #{queueNumber},
                queue_order = #{queueOrder},
                requires_recheck_in = 0
            WHERE id = #{id} AND requires_recheck_in = 1
            """)
    int reactivateAfterCheckIn(DepartmentCheckIn checkIn);

    @Insert("""
            INSERT IGNORE INTO department_check_in_sequence
                (session_id, next_number)
            VALUES (#{sessionId}, 1)
            """)
    int initializeSequence(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT next_number
            FROM department_check_in_sequence
            WHERE session_id = #{sessionId}
            FOR UPDATE
            """)
    int selectNextNumberForUpdate(@Param("sessionId") Long sessionId);

    @Update("""
            UPDATE department_check_in_sequence
            SET next_number = next_number + 1
            WHERE session_id = #{sessionId}
            """)
    int incrementNextNumber(@Param("sessionId") Long sessionId);
}
