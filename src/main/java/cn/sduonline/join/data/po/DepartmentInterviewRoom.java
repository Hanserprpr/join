package cn.sduonline.join.data.po;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DepartmentInterviewRoom {
    private Long id;
    private Long departmentId;
    private Long sessionId;
    private String name;
    private String status;
    private String createdBy;
    private LocalDateTime createdAt;
}
