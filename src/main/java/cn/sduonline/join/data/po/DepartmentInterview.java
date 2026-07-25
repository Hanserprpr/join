package cn.sduonline.join.data.po;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DepartmentInterview {
    private Long id;
    private Long departmentId;
    private Long checkInId;
    private Long applicationId;
    private String candidateCasId;
    private String candidateName;
    private String interviewerCasId;
    private Integer queueNumber;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
