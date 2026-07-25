package cn.sduonline.join.security.scope;

/**
 * 系统权限编码
 * 注解使用枚举，便于 IDE 补全并避免字符串拼写错误
 */
public enum PermissionCode {

    RECRUITMENT_MANAGE("recruitment:manage", "管理部门纳新信息及活动"),
    APPLICATION_READ("application:read", "查看报名信息"),
    APPLICATION_EXPORT("application:export", "导出报名信息"),
    CHECK_IN_MANAGE("check-in:manage", "展示签到二维码及管理签到"),
    INTERVIEW_MANAGE("interview:manage", "创建及修改面试安排"),
    INTERVIEW_EVALUATE("interview:evaluate", "进行面试考核"),
    ADMISSION_MANAGE("admission:manage", "管理录取结果"),
    NOTIFICATION_MANAGE("notification:manage", "发布纳新通知"),
    STATISTICS_READ("statistics:read", "查看纳新统计"),
    ADMIN_ASSISTANT_ASSIGN("admin:assistant:assign", "任命部门辅助管理员"),
    SYSTEM_USER_MANAGE("system:user:manage", "管理平台用户"),
    SYSTEM_ORGANIZATION_MANAGE(
            "system:organization:manage",
            "管理板块、工作站和部门"
    ),
    SYSTEM_CONFIG_MANAGE("system:config:manage", "管理系统配置"),
    ADMIN_ROLE_ASSIGN("admin:role:assign", "分配管理员角色");

    private final String code;
    private final String description;

    PermissionCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 获取权限编码
     *
     * @return 权限编码
     */
    public String code() {
        return code;
    }

    /**
     * 获取权限中文说明
     *
     * @return 权限中文说明
     */
    public String description() {
        return description;
    }
}
