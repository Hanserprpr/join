package cn.sduonline.join.data.po;

import cn.sduonline.join.data.enums.ApplicationStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DepartmentApplication {
    private Long id;
    private Long departmentId;
    private String departmentName;
    private Long workstationId;
    private String workstationName;
    private Long boardId;
    private String boardName;
    private String casId;
    private String applicantName;
    private String college;
    private String major;
    private Integer grade;
    private String phone;
    private String email;
    private String qq;
    private ApplicationStatus status;
    private LocalDateTime submittedAt;
    private Long interviewId;
    private Boolean interviewed;
    private BigDecimal score;
}
