package cn.sduonline.join.data.po;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdmissionEmailOutbox {
    private Long id;
    private Long applicationId;
    private String recipient;
    private String subject;
    private String content;
    private String status;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lockedAt;
    private String lastError;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
