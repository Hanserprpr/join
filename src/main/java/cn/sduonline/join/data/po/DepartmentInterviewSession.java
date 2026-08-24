package cn.sduonline.join.data.po;

import cn.sduonline.join.data.enums.InterviewSessionStatus;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DepartmentInterviewSession {
    private Long id;
    private Long departmentId;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String location;
    private Integer checkInLimit;
    private Boolean qrCheckInEnabled;
    private Integer qrCodeTtlSeconds;
    private InterviewSessionStatus status;
    private LocalDateTime publishedAt;
    private LocalDateTime endedAt;
}
