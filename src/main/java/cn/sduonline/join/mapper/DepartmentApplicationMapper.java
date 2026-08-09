package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentApplicationAnswer;
import cn.sduonline.join.data.po.DepartmentApplicationAnswerOption;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DepartmentApplicationMapper {

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM department_application a
            JOIN `user` u ON u.cas_id = a.cas_id
            LEFT JOIN department_interview i ON i.application_id = a.id
            WHERE a.department_id = #{departmentId}
            <if test="keyword != null and keyword != ''">
              AND (u.name LIKE CONCAT('%', #{keyword}, '%')
                   OR a.cas_id LIKE CONCAT('%', #{keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                   OR u.qq LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="college != null and college != ''">
              AND u.college = #{college}
            </if>
            <if test="grade != null">
              AND u.grade = #{grade}
            </if>
            <if test="interviewed != null and interviewed">
              AND i.ended_at IS NOT NULL
            </if>
            <if test="interviewed != null and interviewed == false">
              AND (i.id IS NULL OR i.ended_at IS NULL)
            </if>
            </script>
            """)
    long countApplications(
            @Param("departmentId") Long departmentId,
            @Param("keyword") String keyword,
            @Param("college") String college,
            @Param("grade") Integer grade,
            @Param("interviewed") Boolean interviewed
    );

    @Select("""
            <script>
            SELECT a.id, a.department_id, a.cas_id,
                   u.name AS applicant_name, u.college, u.major, u.grade,
                   u.phone, u.email, u.qq, a.status, a.submitted_at,
                   i.id AS interview_id,
                   CASE WHEN i.ended_at IS NOT NULL
                     THEN TRUE ELSE FALSE
                   END AS interviewed,
                   i.score
            FROM department_application a
            JOIN `user` u ON u.cas_id = a.cas_id
            LEFT JOIN department_interview i ON i.application_id = a.id
            WHERE a.department_id = #{departmentId}
            <if test="keyword != null and keyword != ''">
              AND (u.name LIKE CONCAT('%', #{keyword}, '%')
                   OR a.cas_id LIKE CONCAT('%', #{keyword}, '%')
                   OR u.phone LIKE CONCAT('%', #{keyword}, '%')
                   OR u.qq LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="college != null and college != ''">
              AND u.college = #{college}
            </if>
            <if test="grade != null">
              AND u.grade = #{grade}
            </if>
            <if test="interviewed != null and interviewed">
              AND i.ended_at IS NOT NULL
            </if>
            <if test="interviewed != null and interviewed == false">
              AND (i.id IS NULL OR i.ended_at IS NULL)
            </if>
            <choose>
              <when test="sortBy == 'score' and sortOrder == 'asc'">
                ORDER BY i.score IS NULL ASC, i.score ASC, a.id DESC
              </when>
              <when test="sortBy == 'score' and sortOrder == 'desc'">
                ORDER BY i.score IS NULL ASC, i.score DESC, a.id DESC
              </when>
              <otherwise>
                ORDER BY a.submitted_at DESC, a.id DESC
              </otherwise>
            </choose>
            <if test="limit != null">
              LIMIT #{limit} OFFSET #{offset}
            </if>
            </script>
            """)
    java.util.List<DepartmentApplication> selectApplications(
            @Param("departmentId") Long departmentId,
            @Param("keyword") String keyword,
            @Param("college") String college,
            @Param("grade") Integer grade,
            @Param("interviewed") Boolean interviewed,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("offset") Integer offset,
            @Param("limit") Integer limit
    );

    @Select("""
            SELECT a.id, a.department_id, a.cas_id,
                   u.name AS applicant_name, u.college, u.major, u.grade,
                   u.phone, u.email, u.qq, a.status, a.submitted_at,
                   i.id AS interview_id,
                   CASE WHEN i.ended_at IS NOT NULL
                     THEN TRUE ELSE FALSE
                   END AS interviewed,
                   i.score
            FROM department_application a
            JOIN `user` u ON u.cas_id = a.cas_id
            LEFT JOIN department_interview i ON i.application_id = a.id
            WHERE a.department_id = #{departmentId}
              AND a.id = #{applicationId}
            """)
    DepartmentApplication selectApplicationDetail(
            @Param("departmentId") Long departmentId,
            @Param("applicationId") Long applicationId
    );

    @Select("""
            SELECT id, application_id, question_id, question_title,
                   question_type, answer_text
            FROM department_application_answer
            WHERE application_id = #{applicationId}
            ORDER BY id ASC
            """)
    java.util.List<DepartmentApplicationAnswer> selectAnswers(
            @Param("applicationId") Long applicationId
    );

    @Select("""
            SELECT answer_id, option_id, option_content
            FROM department_application_answer_option
            WHERE answer_id = #{answerId}
            ORDER BY option_id ASC
            """)
    java.util.List<DepartmentApplicationAnswerOption> selectAnswerOptions(
            @Param("answerId") Long answerId
    );

    @Select("""
            SELECT COUNT(1)
            FROM department_application
            WHERE department_id = #{departmentId} AND cas_id = #{casId}
            """)
    long countByDepartmentAndUser(
            @Param("departmentId") Long departmentId,
            @Param("casId") String casId
    );

    @Select("""
            SELECT id, department_id, cas_id, status, submitted_at
            FROM department_application
            WHERE department_id = #{departmentId} AND cas_id = #{casId}
            """)
    DepartmentApplication selectByDepartmentAndUser(
            @Param("departmentId") Long departmentId,
            @Param("casId") String casId
    );

    @Select("""
            SELECT id, department_id, cas_id, status, submitted_at
            FROM department_application
            WHERE department_id = #{departmentId} AND id = #{applicationId}
            FOR UPDATE
            """)
    DepartmentApplication selectByDepartmentAndIdForUpdate(
            @Param("departmentId") Long departmentId,
            @Param("applicationId") Long applicationId
    );

    @Update("""
            UPDATE department_application
            SET status = #{status}
            WHERE department_id = #{departmentId} AND id = #{applicationId}
            """)
    int updateStatus(
            @Param("departmentId") Long departmentId,
            @Param("applicationId") Long applicationId,
            @Param("status") cn.sduonline.join.data.enums.ApplicationStatus status
    );

    @Select("""
            SELECT a.id, a.department_id, a.cas_id, a.status, a.submitted_at,
                   u.name AS applicant_name, u.email
            FROM department_application a
            JOIN `user` u ON u.cas_id = a.cas_id
            WHERE a.department_id = #{departmentId}
              AND a.status = 'ADMISSION_DRAFT'
            ORDER BY a.id ASC
            FOR UPDATE
            """)
    java.util.List<DepartmentApplication> selectAdmissionDraftsForUpdate(
            @Param("departmentId") Long departmentId
    );

    @Update("""
            <script>
            UPDATE department_application
            SET status = 'ADMITTED'
            WHERE department_id = #{departmentId}
              AND status = 'ADMISSION_DRAFT'
              AND id IN
              <foreach collection="applicationIds" item="applicationId"
                       open="(" separator="," close=")">
                #{applicationId}
              </foreach>
            </script>
            """)
    int publishAdmissionDraftsByIds(
            @Param("departmentId") Long departmentId,
            @Param("applicationIds") java.util.List<Long> applicationIds
    );

    @Insert("""
            INSERT INTO department_application
                (department_id, cas_id, status, submitted_at)
            VALUES
                (#{departmentId}, #{casId}, #{status}, #{submittedAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertApplication(DepartmentApplication application);

    @Insert("""
            INSERT INTO department_application_answer
                (application_id, question_id, question_title, question_type, answer_text)
            VALUES
                (#{applicationId}, #{questionId}, #{questionTitle}, #{questionType},
                 #{answerText})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAnswer(DepartmentApplicationAnswer answer);

    @Insert("""
            INSERT INTO department_application_answer_option
                (answer_id, option_id, option_content)
            VALUES
                (#{answerId}, #{optionId}, #{optionContent})
            """)
    int insertAnswerOption(DepartmentApplicationAnswerOption option);
}
