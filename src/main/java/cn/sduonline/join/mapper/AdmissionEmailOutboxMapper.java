package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.AdmissionEmailOutbox;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdmissionEmailOutboxMapper {

    @Insert("""
            INSERT INTO admission_email_outbox
                (application_id, recipient, subject, content, status,
                 next_attempt_at)
            VALUES
                (#{applicationId}, #{recipient}, #{subject}, #{content},
                 #{status}, #{nextAttemptAt})
            """)
    int insert(
            @Param("applicationId") Long applicationId,
            @Param("recipient") String recipient,
            @Param("subject") String subject,
            @Param("content") String content,
            @Param("status") String status,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt
    );

    @Select("""
            SELECT id, application_id, recipient, subject, content, status,
                   attempts, next_attempt_at, locked_at, last_error,
                   sent_at, created_at, updated_at
            FROM admission_email_outbox
            WHERE (status = 'PENDING' AND next_attempt_at <= #{now})
               OR (status = 'PROCESSING' AND locked_at < #{staleBefore})
            ORDER BY next_attempt_at, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    AdmissionEmailOutbox selectNextForUpdate(
            @Param("now") LocalDateTime now,
            @Param("staleBefore") LocalDateTime staleBefore
    );

    @Update("""
            UPDATE admission_email_outbox
            SET status = 'PROCESSING', attempts = attempts + 1,
                locked_at = #{lockedAt}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND status IN ('PENDING', 'PROCESSING')
            """)
    int markProcessing(
            @Param("id") Long id,
            @Param("lockedAt") LocalDateTime lockedAt
    );

    @Update("""
            UPDATE admission_email_outbox
            SET status = 'SENT', sent_at = #{sentAt}, locked_at = NULL,
                last_error = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markSent(
            @Param("id") Long id,
            @Param("sentAt") LocalDateTime sentAt
    );

    @Update("""
            UPDATE admission_email_outbox
            SET status = 'PENDING', next_attempt_at = #{nextAttemptAt},
                locked_at = NULL, last_error = #{lastError},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id} AND status = 'PROCESSING'
            """)
    int markRetry(
            @Param("id") Long id,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );
}
