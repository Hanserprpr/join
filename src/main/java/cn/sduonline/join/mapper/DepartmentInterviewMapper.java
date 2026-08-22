package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentInterview;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentInterviewMapper {

    @Select("""
            SELECT c.id AS check_in_id, c.application_id,
                   c.cas_id AS candidate_cas_id,
                   candidate.name AS candidate_name,
                   c.queue_number, c.queue_order, c.pass_count, c.checked_in_at,
                   CASE
                     WHEN c.requires_recheck_in = TRUE
                       THEN 'RECHECK_IN_REQUIRED'
                     WHEN own_interview.ended_at IS NOT NULL THEN 'COMPLETED'
                     WHEN own_active.interview_id IS NOT NULL THEN 'INTERVIEWING'
                     WHEN other_active.interview_id IS NOT NULL
                       THEN 'INTERVIEWING_ELSEWHERE'
                     ELSE 'WAITING'
                   END AS status,
                   CASE WHEN own_active.interview_id IS NOT NULL
                     THEN own_interview.interviewer_cas_id
                     ELSE NULL
                   END AS interviewer_cas_id,
                   CASE WHEN own_active.interview_id IS NOT NULL
                     THEN interviewer.name
                     ELSE NULL
                   END AS interviewer_name,
                   own_interview.started_at, own_interview.ended_at,
                   c.priority
            FROM department_check_in c
            JOIN department_interview_session s
              ON s.id = c.session_id AND s.status = 'PUBLISHED'
            JOIN `user` candidate ON candidate.cas_id = c.cas_id
            LEFT JOIN department_interview own_interview
                   ON own_interview.check_in_id = c.id
            LEFT JOIN department_interview_active own_active
                   ON own_active.interview_id = own_interview.id
            LEFT JOIN department_interview_active other_active
                   ON other_active.candidate_cas_id = c.cas_id
                  AND other_active.interview_id <> COALESCE(own_interview.id, -1)
            LEFT JOIN `user` interviewer
                   ON interviewer.cas_id = own_interview.interviewer_cas_id
            WHERE c.department_id = #{departmentId}
            ORDER BY c.priority DESC, c.queue_order ASC
            """)
    java.util.List<InterviewQueueItemVO> selectQueue(
            @Param("departmentId") Long departmentId
    );

    @Select("""
            SELECT c.id AS check_in_id, c.application_id,
                   c.cas_id AS candidate_cas_id,
                   candidate.name AS candidate_name,
                   c.queue_number, c.queue_order, c.pass_count, c.checked_in_at,
                   CASE
                     WHEN c.requires_recheck_in = TRUE
                       THEN 'RECHECK_IN_REQUIRED'
                     WHEN own_interview.ended_at IS NOT NULL THEN 'COMPLETED'
                     WHEN own_active.interview_id IS NOT NULL THEN 'INTERVIEWING'
                     WHEN other_active.interview_id IS NOT NULL
                       THEN 'INTERVIEWING_ELSEWHERE'
                     ELSE 'WAITING'
                   END AS status,
                   CASE WHEN own_active.interview_id IS NOT NULL
                     THEN own_interview.interviewer_cas_id
                     ELSE NULL
                   END AS interviewer_cas_id,
                   CASE WHEN own_active.interview_id IS NOT NULL
                     THEN interviewer.name
                     ELSE NULL
                   END AS interviewer_name,
                   own_interview.started_at, own_interview.ended_at,
                   c.priority
            FROM department_check_in c
            JOIN department_interview_session s
              ON s.id = c.session_id AND s.status = 'PUBLISHED'
            JOIN `user` candidate ON candidate.cas_id = c.cas_id
            LEFT JOIN department_interview own_interview
                   ON own_interview.check_in_id = c.id
            LEFT JOIN department_interview_active own_active
                   ON own_active.interview_id = own_interview.id
            LEFT JOIN department_interview_active other_active
                   ON other_active.candidate_cas_id = c.cas_id
                  AND other_active.interview_id <> COALESCE(own_interview.id, -1)
            LEFT JOIN `user` interviewer
                   ON interviewer.cas_id = own_interview.interviewer_cas_id
            WHERE c.department_id = #{departmentId}
              AND c.cas_id = #{casId}
            """)
    InterviewQueueItemVO selectCandidateQueueItem(
            @Param("departmentId") Long departmentId,
            @Param("casId") String casId
    );

    @Select("""
            SELECT COUNT(1)
            FROM department_check_in ahead
            JOIN department_interview_session s
              ON s.id = ahead.session_id AND s.status = 'PUBLISHED'
            LEFT JOIN department_interview own_interview
                   ON own_interview.check_in_id = ahead.id
            LEFT JOIN department_interview_active other_active
                   ON other_active.candidate_cas_id = ahead.cas_id
                  AND other_active.interview_id
                      <> COALESCE(own_interview.id, -1)
            WHERE ahead.department_id = #{departmentId}
              AND ahead.requires_recheck_in = FALSE
              AND (
                (#{priority} = TRUE
                  AND ahead.priority = TRUE
                  AND ahead.queue_order < #{queueOrder})
                OR
                (#{priority} = FALSE
                  AND (ahead.priority = TRUE
                    OR ahead.queue_order < #{queueOrder}))
              )
              AND (
                own_interview.id IS NULL
                OR own_interview.ended_at IS NULL
              )
              AND (
                own_interview.id IS NOT NULL
                OR other_active.interview_id IS NULL
              )
            """)
    int countPeopleAhead(
            @Param("departmentId") Long departmentId,
            @Param("queueOrder") Long queueOrder,
            @Param("priority") Boolean priority
    );

    @Select("""
            SELECT i.queue_number
            FROM department_interview_active a
            JOIN department_interview i ON i.id = a.interview_id
            JOIN department_check_in c ON c.id = i.check_in_id
            JOIN department_interview_session s
              ON s.id = c.session_id AND s.status = 'PUBLISHED'
            WHERE i.department_id = #{departmentId}
            ORDER BY i.queue_number ASC
            """)
    java.util.List<Integer> selectInterviewingQueueNumbers(
            @Param("departmentId") Long departmentId
    );

    @Select("""
            SELECT i.id, i.department_id, i.room_id, i.check_in_id,
                   i.application_id, i.candidate_cas_id,
                   candidate.name AS candidate_name, i.interviewer_cas_id,
                   interviewer.name AS interviewer_name,
                   i.queue_number, i.started_at, i.ended_at
            FROM department_interview_active a
            JOIN department_interview i ON i.id = a.interview_id
            JOIN `user` candidate ON candidate.cas_id = i.candidate_cas_id
            LEFT JOIN `user` interviewer
                   ON interviewer.cas_id = i.interviewer_cas_id
            WHERE a.room_id = #{roomId}
            """)
    DepartmentInterview selectActiveByRoom(@Param("roomId") Long roomId);

    @Select("""
            SELECT i.id, i.department_id, i.room_id,
                   i.check_in_id, i.application_id,
                   i.candidate_cas_id,
                   candidate.name AS candidate_name,
                   i.interviewer_cas_id,
                   interviewer.name AS interviewer_name,
                   i.queue_number,
                   i.started_at, i.ended_at
            FROM department_interview i
            JOIN `user` candidate ON candidate.cas_id = i.candidate_cas_id
            LEFT JOIN `user` interviewer
                   ON interviewer.cas_id = i.interviewer_cas_id
            WHERE i.id = #{interviewId}
              AND i.department_id = #{departmentId}
            """)
    DepartmentInterview selectByIdAndDepartment(
            @Param("departmentId") Long departmentId,
            @Param("interviewId") Long interviewId
    );

    @Select("""
            SELECT c.id AS check_in_id, c.department_id, c.application_id,
                   c.cas_id AS candidate_cas_id, u.name AS candidate_name,
                   c.queue_number
            FROM department_check_in c
            JOIN department_interview_session s
              ON s.id = c.session_id AND s.status = 'PUBLISHED'
            JOIN `user` u ON u.cas_id = c.cas_id
            LEFT JOIN department_interview i ON i.check_in_id = c.id
            LEFT JOIN department_interview_active a
                   ON a.candidate_cas_id = c.cas_id
            WHERE c.department_id = #{departmentId}
              AND c.session_id = #{sessionId}
              AND i.id IS NULL
              AND a.interview_id IS NULL
              AND c.requires_recheck_in = FALSE
            ORDER BY c.priority DESC, c.queue_order ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    DepartmentInterview selectNextWaitingForUpdate(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId
    );

    @Select("""
            SELECT pass_delay_count, max_pass_count, pass_mode
            FROM department
            WHERE id = #{departmentId}
            """)
    cn.sduonline.join.data.dto.InterviewQueueConfigVO selectQueueConfig(
            @Param("departmentId") Long departmentId
    );

    @Update("""
            UPDATE department
            SET pass_delay_count = #{passDelayCount},
                max_pass_count = #{maxPassCount},
                pass_mode = #{passMode}
            WHERE id = #{departmentId}
            """)
    int updateQueueConfig(
            @Param("departmentId") Long departmentId,
            @Param("passDelayCount") int passDelayCount,
            @Param("maxPassCount") int maxPassCount,
            @Param("passMode") cn.sduonline.join.data.enums.InterviewPassMode passMode
    );

    @Select("""
            SELECT id, department_id, session_id, application_id, cas_id,
                   checked_in_at, queue_number, queue_order, pass_count,
                   priority, requires_recheck_in
            FROM department_check_in
            WHERE id = #{checkInId}
            FOR UPDATE
            """)
    cn.sduonline.join.data.po.DepartmentCheckIn selectCheckInForUpdate(
            @Param("checkInId") Long checkInId
    );

    @Select("""
            SELECT c.id, c.department_id, c.session_id, c.application_id,
                   c.cas_id, c.checked_in_at, c.queue_number, c.queue_order,
                   c.pass_count, c.priority
            FROM department_check_in c
            JOIN department_interview_session s
              ON s.id = c.session_id AND s.status = 'PUBLISHED'
            LEFT JOIN department_interview i ON i.check_in_id = c.id
            WHERE c.department_id = #{departmentId}
              AND i.id IS NULL
              AND c.requires_recheck_in = FALSE
            ORDER BY c.priority DESC, c.queue_order ASC
            FOR UPDATE
            """)
    java.util.List<cn.sduonline.join.data.po.DepartmentCheckIn>
            selectReorderableQueueForUpdate(
                    @Param("departmentId") Long departmentId
            );

    @Update("""
            UPDATE department_check_in
            SET queue_order = #{queueOrder},
                pass_count = #{passCount}
            WHERE id = #{id}
            """)
    int updateCheckInQueue(
            cn.sduonline.join.data.po.DepartmentCheckIn checkIn
    );

    @Update("""
            UPDATE department_check_in
            SET pass_count = #{passCount}, priority = 0,
                requires_recheck_in = 1
            WHERE id = #{id}
            """)
    int requireCheckInAgain(
            cn.sduonline.join.data.po.DepartmentCheckIn checkIn
    );

    @Insert("""
            INSERT INTO department_interview
                (department_id, room_id, check_in_id, application_id,
                 candidate_cas_id, interviewer_cas_id,
                 queue_number, started_at)
            VALUES
                (#{departmentId}, #{roomId}, #{checkInId}, #{applicationId},
                 #{candidateCasId}, #{interviewerCasId},
                 #{queueNumber}, #{startedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRoomInterview(DepartmentInterview interview);

    @Insert("""
            INSERT INTO department_interview_active
                (interview_id, room_id, check_in_id, candidate_cas_id)
            VALUES
                (#{id}, #{roomId}, #{checkInId}, #{candidateCasId})
            """)
    int insertRoomActive(DepartmentInterview interview);

    @Update("""
            UPDATE department_interview
            SET ended_at = #{endedAt}
            WHERE id = #{id} AND ended_at IS NULL
            """)
    int finishInterview(
            @Param("id") Long id,
            @Param("endedAt") java.time.LocalDateTime endedAt
    );

    @Delete("""
            DELETE FROM department_interview_active
            WHERE interview_id = #{interviewId}
            """)
    int deleteActive(@Param("interviewId") Long interviewId);

    @Delete("""
            DELETE FROM department_interview
            WHERE id = #{interviewId}
            """)
    int deleteInterview(@Param("interviewId") Long interviewId);
}
