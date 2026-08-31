package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentInterviewSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentInterviewSessionMapper {

    @Insert("""
            INSERT INTO department_interview_session
                (department_id, name, starts_at, ends_at, location,
                 check_in_limit, qr_check_in_enabled,
                 qr_code_ttl_seconds, status)
            VALUES
                (#{departmentId}, #{name}, #{startsAt}, #{endsAt}, #{location},
                 #{checkInLimit}, #{qrCheckInEnabled},
                 #{qrCodeTtlSeconds}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DepartmentInterviewSession session);

    @Select("""
            SELECT id, department_id, name, starts_at, ends_at, location,
                   check_in_limit, qr_check_in_enabled,
                   qr_code_ttl_seconds, status,
                   published_at, ended_at
            FROM department_interview_session
            WHERE id = #{sessionId} AND department_id = #{departmentId}
            """)
    DepartmentInterviewSession selectById(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId
    );

    @Select("""
            SELECT id, department_id, name, starts_at, ends_at, location,
                   check_in_limit, qr_check_in_enabled,
                   qr_code_ttl_seconds, status,
                   published_at, ended_at
            FROM department_interview_session
            WHERE department_id = #{departmentId}
              AND status = 'PUBLISHED'
            ORDER BY starts_at ASC, id ASC
            """)
    java.util.List<DepartmentInterviewSession> selectPublished(
            @Param("departmentId") Long departmentId
    );

    @Select("""
            SELECT id, department_id, name, starts_at, ends_at, location,
                   check_in_limit, qr_check_in_enabled,
                   qr_code_ttl_seconds, status,
                   published_at, ended_at
            FROM department_interview_session
            WHERE id = #{sessionId} AND department_id = #{departmentId}
              AND status = 'PUBLISHED'
            """)
    DepartmentInterviewSession selectPublishedById(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId
    );

    @Select("""
            SELECT id, department_id, name, starts_at, ends_at, location,
                   check_in_limit, qr_check_in_enabled,
                   qr_code_ttl_seconds, status,
                   published_at, ended_at
            FROM department_interview_session
            WHERE id = #{sessionId} AND department_id = #{departmentId}
              AND status = 'PUBLISHED'
            FOR UPDATE
            """)
    DepartmentInterviewSession selectPublishedForUpdate(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId
    );

    @Select("""
            SELECT id, department_id, name, starts_at, ends_at, location,
                   check_in_limit, qr_check_in_enabled,
                   qr_code_ttl_seconds, status,
                   published_at, ended_at
            FROM department_interview_session
            WHERE department_id = #{departmentId}
            ORDER BY starts_at DESC, id DESC
            """)
    java.util.List<DepartmentInterviewSession> selectAll(
            @Param("departmentId") Long departmentId
    );

    @Update("""
            UPDATE department_interview_session
            SET name = #{name}, starts_at = #{startsAt}, ends_at = #{endsAt},
                location = #{location}, check_in_limit = #{checkInLimit},
                qr_check_in_enabled = #{qrCheckInEnabled},
                qr_code_ttl_seconds = #{qrCodeTtlSeconds}
            WHERE id = #{id} AND department_id = #{departmentId}
              AND status <> 'ENDED'
            """)
    int update(DepartmentInterviewSession session);

    @Update("""
            UPDATE department_interview_session
            SET status = 'PUBLISHED', published_at = #{publishedAt}
            WHERE id = #{sessionId} AND department_id = #{departmentId}
              AND status = 'DRAFT'
            """)
    int publish(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId,
            @Param("publishedAt") java.time.LocalDateTime publishedAt
    );

    @Update("""
            UPDATE department_interview_session
            SET status = 'ENDED', ended_at = #{endedAt}
            WHERE id = #{sessionId} AND department_id = #{departmentId}
              AND status = 'PUBLISHED'
            """)
    int end(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId,
            @Param("endedAt") java.time.LocalDateTime endedAt
    );

    @Select("""
            SELECT COUNT(1)
            FROM department_check_in
            WHERE session_id = #{sessionId}
            """)
    int countCheckIns(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT COUNT(1)
            FROM department_interview_active a
            JOIN department_check_in c ON c.id = a.check_in_id
            WHERE c.session_id = #{sessionId}
            """)
    int countActiveInterviews(@Param("sessionId") Long sessionId);

    @Insert("""
            INSERT IGNORE INTO department_interview_carryover
                (department_id, application_id, source_session_id,
                 status, created_at)
            SELECT c.department_id, c.application_id, c.session_id,
                   'PENDING', CURRENT_TIMESTAMP
            FROM department_check_in c
            LEFT JOIN department_interview i ON i.check_in_id = c.id
            WHERE c.session_id = #{sessionId}
              AND i.id IS NULL
              AND c.requires_recheck_in = FALSE
            """)
    int createCarryovers(@Param("sessionId") Long sessionId);

    @Update("""
            UPDATE department_interview_carryover
            SET status = 'CANCELLED'
            WHERE target_session_id = #{sessionId}
              AND status = 'PENDING'
            """)
    int cancelUnusedCarryovers(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT id
            FROM department_interview_carryover
            WHERE department_id = #{departmentId}
              AND application_id = #{applicationId}
              AND (target_session_id IS NULL
                   OR target_session_id = #{sessionId})
              AND status = 'PENDING'
            ORDER BY created_at ASC, id ASC
            LIMIT 1
            FOR UPDATE
            """)
    Long selectPendingCarryoverForUpdate(
            @Param("departmentId") Long departmentId,
            @Param("applicationId") Long applicationId,
            @Param("sessionId") Long sessionId
    );

    @Update("""
            UPDATE department_interview_carryover
            SET status = 'USED', target_session_id = #{sessionId},
                used_at = CURRENT_TIMESTAMP
            WHERE id = #{carryoverId} AND status = 'PENDING'
            """)
    int useCarryover(
            @Param("carryoverId") Long carryoverId,
            @Param("sessionId") Long sessionId
    );
}
