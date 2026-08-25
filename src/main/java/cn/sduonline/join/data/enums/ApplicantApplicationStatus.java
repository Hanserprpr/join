package cn.sduonline.join.data.enums;

import cn.sduonline.join.data.po.DepartmentApplication;

/**
 * 对报名人公开的报名状态。
 * 后台的拟录取草稿不属于用户可见状态。
 */
public enum ApplicantApplicationStatus {
    SUBMITTED,
    INTERVIEW_COMPLETED,
    ADMITTED;

    public static ApplicantApplicationStatus from(
            DepartmentApplication application
    ) {
        if (application.getStatus() == ApplicationStatus.ADMITTED) {
            return ADMITTED;
        }
        if (Boolean.TRUE.equals(application.getInterviewed())) {
            return INTERVIEW_COMPLETED;
        }
        return SUBMITTED;
    }
}
