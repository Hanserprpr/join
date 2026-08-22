package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentCheckIn;
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

    @Select("""
            SELECT id, department_id, application_id, cas_id,
                   session_id, checked_in_at, queue_number, queue_order,
                   pass_count, priority, requires_recheck_in
            FROM department_check_in
            WHERE session_id = #{sessionId}
              AND application_id = #{applicationId}
            """)
    DepartmentCheckIn selectBySessionAndApplication(
            @Param("sessionId") Long sessionId,
            @Param("applicationId") Long applicationId
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
