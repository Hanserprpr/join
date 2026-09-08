package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO;
import cn.sduonline.join.data.dto.InterviewEvaluationVO;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentInterviewRoomMapper {

    @Insert("""
            INSERT INTO department_interview_room
                (department_id, session_id, name, status, created_by)
            VALUES
                (#{departmentId}, #{sessionId}, #{name}, 'OPEN', #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DepartmentInterviewRoom room);

    @Select("""
            SELECT id
            FROM department_interview_room
            WHERE session_id = #{sessionId} AND open_name = #{name}
            FOR UPDATE
            """)
    Long selectOpenIdBySessionAndNameForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("name") String name
    );

    @Select("""
            SELECT id, department_id, session_id, name, status,
                   created_by, created_at
            FROM department_interview_room
            WHERE department_id = #{departmentId}
              AND session_id = #{sessionId}
            ORDER BY id
            """)
    List<DepartmentInterviewRoom> selectBySession(
            @Param("departmentId") Long departmentId,
            @Param("sessionId") Long sessionId
    );

    @Select("""
            SELECT id, department_id, session_id, name, status,
                   created_by, created_at
            FROM department_interview_room
            WHERE id = #{roomId} AND department_id = #{departmentId}
            """)
    DepartmentInterviewRoom selectById(
            @Param("departmentId") Long departmentId,
            @Param("roomId") Long roomId
    );

    @Select("""
            SELECT id, department_id, session_id, name, status,
                   created_by, created_at
            FROM department_interview_room
            WHERE id = #{roomId} AND department_id = #{departmentId}
            FOR UPDATE
            """)
    DepartmentInterviewRoom selectByIdForUpdate(
            @Param("departmentId") Long departmentId,
            @Param("roomId") Long roomId
    );

    @Insert("""
            INSERT INTO department_interview_room_member
                (room_id, admin_cas_id)
            VALUES (#{roomId}, #{casId})
            """)
    int insertMember(
            @Param("roomId") Long roomId,
            @Param("casId") String casId
    );

    @Delete("""
            DELETE FROM department_interview_room_member
            WHERE room_id = #{roomId} AND admin_cas_id = #{casId}
            """)
    int deleteMember(
            @Param("roomId") Long roomId,
            @Param("casId") String casId
    );

    @Select("""
            SELECT admin_cas_id
            FROM department_interview_room_member
            WHERE room_id = #{roomId}
            ORDER BY joined_at, admin_cas_id
            """)
    List<String> selectMemberCasIds(@Param("roomId") Long roomId);

    @Select("""
            SELECT COUNT(*)
            FROM department_interview_room_member
            WHERE room_id = #{roomId} AND admin_cas_id = #{casId}
            """)
    int countMember(
            @Param("roomId") Long roomId,
            @Param("casId") String casId
    );

    @Update("""
            UPDATE department_interview_room
            SET status = 'CLOSED'
            WHERE id = #{roomId} AND department_id = #{departmentId}
              AND status = 'OPEN'
            """)
    int close(
            @Param("departmentId") Long departmentId,
            @Param("roomId") Long roomId
    );

    @Select("""
            SELECT m.admin_cas_id AS cas_id, u.name,
                   CASE WHEN e.interview_id IS NULL THEN FALSE ELSE TRUE END
                       AS submitted,
                   e.submitted_at
            FROM department_interview_room_member m
            JOIN `user` u ON u.cas_id = m.admin_cas_id
            LEFT JOIN department_interview_evaluation e
              ON e.interview_id = #{interviewId}
             AND e.admin_cas_id = m.admin_cas_id
            WHERE m.room_id = #{roomId}
            ORDER BY m.joined_at, m.admin_cas_id
            """)
    List<InterviewRoomMemberStatusVO> selectMemberStatuses(
            @Param("roomId") Long roomId,
            @Param("interviewId") Long interviewId
    );

    @Insert("""
            INSERT INTO department_interview_evaluation
                (interview_id, admin_cas_id, score, evaluation)
            VALUES
                (#{interviewId}, #{adminCasId}, #{score}, #{evaluation})
            ON DUPLICATE KEY UPDATE
                score = VALUES(score),
                evaluation = VALUES(evaluation),
                submitted_at = CURRENT_TIMESTAMP
            """)
    int upsertEvaluation(
            @Param("interviewId") Long interviewId,
            @Param("adminCasId") String adminCasId,
            @Param("score") BigDecimal score,
            @Param("evaluation") String evaluation
    );

    @Select("""
            SELECT e.interview_id,
                   e.admin_cas_id AS administrator_cas_id,
                   u.name AS administrator_name,
                   e.score, e.evaluation, e.submitted_at
            FROM department_interview_evaluation e
            JOIN `user` u ON u.cas_id = e.admin_cas_id
            JOIN department_interview i ON i.id = e.interview_id
            WHERE e.interview_id = #{interviewId}
              AND i.room_id = #{roomId}
            ORDER BY e.submitted_at, e.admin_cas_id
            """)
    List<InterviewEvaluationVO> selectEvaluations(
            @Param("roomId") Long roomId,
            @Param("interviewId") Long interviewId
    );

    @Select("""
            SELECT e.interview_id,
                   e.admin_cas_id AS administrator_cas_id,
                   u.name AS administrator_name,
                   e.score, e.evaluation, e.submitted_at
            FROM department_interview_evaluation e
            JOIN `user` u ON u.cas_id = e.admin_cas_id
            JOIN department_interview i ON i.id = e.interview_id
            WHERE i.department_id = #{departmentId}
              AND i.candidate_cas_id = #{candidateCasId}
            ORDER BY i.started_at DESC, e.submitted_at, e.admin_cas_id
            """)
    List<InterviewEvaluationVO> selectEvaluationsByCandidate(
            @Param("departmentId") Long departmentId,
            @Param("candidateCasId") String candidateCasId
    );

    @Select("""
            SELECT e.interview_id,
                   e.admin_cas_id AS administrator_cas_id,
                   u.name AS administrator_name,
                   e.score, e.evaluation, e.submitted_at
            FROM department_interview_evaluation e
            JOIN `user` u ON u.cas_id = e.admin_cas_id
            WHERE e.interview_id = #{interviewId}
              AND e.admin_cas_id = #{casId}
            """)
    InterviewEvaluationVO selectEvaluation(
            @Param("interviewId") Long interviewId,
            @Param("casId") String casId
    );
}
