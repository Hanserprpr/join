package cn.sduonline.join.data.po;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DepartmentCheckIn {
    private Long id;
    private Long departmentId;
    private Long applicationId;
    private String casId;
    private LocalDateTime checkedInAt;
    private Integer queueNumber;
    private Long queueOrder;
    private Integer passCount;
}
