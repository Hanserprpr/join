package cn.sduonline.join.data.dto;

import cn.sduonline.join.data.enums.InterviewSessionStatus;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import java.time.LocalDateTime;

/**
 * 面试场次信息
 *
 * @param id 面试场次 ID
 * @param departmentId 部门 ID
 * @param name 场次名称
 * @param startsAt 面试开始时间
 * @param endsAt 面试结束时间
 * @param location 面试地点
 * @param checkInLimit 取号人数上限
 * @param qrCheckInEnabled 是否启用动态二维码签到
 * @param qrCodeTtlSeconds 签到二维码有效秒数
 * @param status 场次状态
 * @param publishedAt 发布时间
 * @param endedAt 实际结束时间
 */
public record InterviewSessionVO(
        Long id,
        Long departmentId,
        String name,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String location,
        Integer checkInLimit,
        Boolean qrCheckInEnabled,
        Integer qrCodeTtlSeconds,
        InterviewSessionStatus status,
        LocalDateTime publishedAt,
        LocalDateTime endedAt
) {
    /**
     * 将面试场次实体转换为返回对象
     *
     * @param source 面试场次实体
     * @return 面试场次返回对象
     */
    public static InterviewSessionVO from(DepartmentInterviewSession source) {
        return new InterviewSessionVO(
                source.getId(), source.getDepartmentId(),
                source.getName(),
                source.getStartsAt(), source.getEndsAt(),
                source.getLocation(), source.getCheckInLimit(),
                source.getQrCheckInEnabled(),
                source.getQrCodeTtlSeconds(),
                source.getStatus(), source.getPublishedAt(),
                source.getEndedAt()
        );
    }
}
