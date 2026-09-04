package cn.sduonline.join.data.enums;

import lombok.Getter;

/**
 * 业务状态码枚举。
 * <p>
 * 优先复用平台已有状态码，纳新系统独有状态使用六位命名空间
 * 六位命名空间约定：11xxxx 通用、12xxxx 认证、13xxxx 用户、
 * 14xxxx 纳新业务、15xxxx 外部依赖、19xxxx 系统
 */
@Getter
public enum BizCode {

    /** 成功。 */
    SUCCESS(0, "请求成功"),

    // ---------- 平台通用错误 ----------
    /** 参数非法。 */
    PARAM_INVALID(40000, "参数范围或格式错误"),
    /** 请求频率过高。 */
    TOO_MANY_REQUESTS(40003, "请求繁忙，请稍后再试"),
    /** 不支持的操作。 */
    NOT_SUPPORTED(40005, "方法不允许"),
    /** 请求的接口或资源不存在。 */
    RESOURCE_NOT_FOUND(40004, "你要找的东西好像走丢啦X﹏X"),

    // ---------- 平台认证与鉴权 ----------
    /** 用户未登录。 */
    NOT_LOGIN(-4, "用户未登录"),
    /** Token 无效。 */
    TOKEN_INVALID(40101, "登录状态已失效"),
    /** 权限不足。 */
    NO_PERMISSION(40103, "无权限访问"),

    // ---------- 13xxxx 用户模块 ----------
    /** 用户不存在。 */
    USER_NOT_FOUND(40200, "用户不存在"),
    /** 用户被禁用。 */
    USER_DISABLED(130002, "用户被禁用"),
    /** 用户名已存在。 */
    USERNAME_EXISTS(130003, "用户名已存在"),
    /** 密码错误。 */
    PASSWORD_ERROR(40100, "学号或统一身份认证密码错误"),
    /** 验证码错误。 */
    VERIFY_CODE_ERROR(40300, "验证码错误"),
    /** 该用户已通过邮箱验证。 */
    EMAIL_ALREADY_VERIFIED(130006, "该用户已通过邮箱验证"),
    /** 邮箱格式不正确。 */
    EMAIL_INVALID(130007, "邮箱格式不正确"),
    /** 邮箱验证链接无效或已过期。 */
    EMAIL_TOKEN_INVALID(130008, "邮箱验证链接无效或已过期"),
    /** 用户尚未完成必填资料。 */
    PROFILE_INCOMPLETE(130009, "用户资料未完成"),
    /** 学院不存在。 */
    COLLEGE_INVALID(130010, "学院不存在"),
    /** 专业不存在或与学院不匹配。 */
    MAJOR_INVALID(130011, "专业不存在或与所选学院不匹配"),
    /** 头像文件为空、格式不支持或内容不是有效图片。 */
    AVATAR_INVALID(130012, "图片文件无效，仅支持 JPEG、PNG、GIF 和 WebP"),
    /** 头像文件超过允许的大小。 */
    AVATAR_TOO_LARGE(130013, "图片文件过大"),
    /** 头像文件被病毒扫描服务判定为恶意文件。 */
    AVATAR_MALWARE_DETECTED(130014, "头像文件未通过安全扫描"),
    /** 当前账号不允许修改个人资料。 */
    PROFILE_UPDATE_FORBIDDEN(130015, "请使用主修账号进入"),

    // ---------- 14xxxx 纳新业务错误 ----------
    /** 操作失败。 */
    OP_FAILED(140001, "操作失败"),
    /** 当前状态不允许该操作。 */
    STATE_NOT_ALLOWED(40002, "当前条件或时间不允许〒▽〒"),
    /** 资源不足。 */
    RESOURCE_NOT_ENOUGH(140003, "资源不足"),
    /** 重复提交。 */
    DUPLICATE_SUBMIT(140004, "重复提交"),
    /** 工作站不存在或未启用。 */
    WORKSTATION_NOT_FOUND(140005, "工作站不存在或未启用"),
    /** 板块不存在或未启用。 */
    BOARD_NOT_FOUND(140006, "板块不存在或未启用"),
    /** 管理员角色不存在。 */
    ROLE_NOT_FOUND(140007, "管理员角色不存在"),
    /** 角色与数据范围不匹配。 */
    ROLE_SCOPE_MISMATCH(140008, "角色与数据范围不匹配"),
    /** 指定的组织范围不存在或未启用。 */
    ORG_SCOPE_NOT_FOUND(140009, "组织范围不存在或未启用"),
    /** 用户已经拥有该角色范围。 */
    ROLE_ASSIGNMENT_EXISTS(140010, "用户已经拥有该角色范围"),
    /** 不能授予同级或更高级角色，或授权范围超出操作者范围。 */
    ROLE_ASSIGNMENT_FORBIDDEN(140011, "无权授予该角色或组织范围"),
    /** 部门不存在。 */
    DEPARTMENT_NOT_FOUND(140012, "部门不存在"),
    /** 报名记录不存在。 */
    APPLICATION_NOT_FOUND(140013, "报名记录不存在"),
    /** 签到二维码无效或已过期。 */
    CHECK_IN_TOKEN_INVALID(140014, "签到二维码无效或已过期"),
    /** 当前用户尚未报名该部门。 */
    CHECK_IN_NOT_REGISTERED(140015, "尚未报名该部门，无法签到"),
    /** 当前部门没有等待面试的签到用户。 */
    INTERVIEW_QUEUE_EMPTY(140016, "当前没有等待面试的用户"),
    /** 当前管理员在该部门没有进行中的面试。 */
    INTERVIEW_NOT_ACTIVE(140018, "当前没有进行中的面试"),
    /** 当前用户尚未在该部门签到。 */
    CHECK_IN_NOT_FOUND(140019, "尚未在该部门签到"),
    /** 当前用户已达到部门允许的最大过号次数 */
    INTERVIEW_PASS_LIMIT_REACHED(140020, "已达到最大过号次数"),
    /** 面试记录不存在。 */
    INTERVIEW_NOT_FOUND(140021, "面试记录不存在"),
    /** 面试场次不存在。 */
    INTERVIEW_SESSION_NOT_FOUND(140022, "面试场次不存在"),
    /** 面试场次状态不允许当前操作。 */
    INTERVIEW_SESSION_STATE_INVALID(140023, "面试场次状态不允许当前操作"),
    /** 当前面试场次签到人数已满。 */
    INTERVIEW_SESSION_FULL(140024, "当前面试场次取号人数已满"),
    /** 当前没有开放签到的面试场次。 */
    INTERVIEW_SESSION_NOT_OPEN(140025, "当前没有开放签到的面试场次"),
    /** 面试室不存在。 */
    INTERVIEW_ROOM_NOT_FOUND(140026, "面试室不存在"),
    /** 面试室已关闭。 */
    INTERVIEW_ROOM_CLOSED(140027, "面试室已关闭"),
    /** 当前管理员尚未加入该面试室。 */
    INTERVIEW_ROOM_NOT_JOINED(140028, "请先加入面试室"),
    /** 当前面试室已有进行中的面试。 */
    INTERVIEW_ROOM_BUSY(140029, "当前面试室已有进行中的面试"),
    /** 仍有管理员未提交评价。 */
    INTERVIEW_EVALUATIONS_PENDING(140030, "仍有管理员未提交评价"),
    /** 未找到该角色分配。 */
    ROLE_ASSIGNMENT_NOT_FOUND(140031, "未找到该角色分配"),
    /** 海报 URL 不属于系统存储或配置的可信白名单。 */
    POSTER_URL_NOT_ALLOWED(140032, "海报地址不在允许的白名单中"),
    /** 报名已进入签到、面试或录取流程，不允许学生自行取消。 */
    APPLICATION_CANNOT_CANCEL(140033, "当前报名已进入后续流程，无法取消"),
    /** 已经开始过面试，不允许取消签到。 */
    CHECK_IN_CANNOT_CANCEL(140034, "已进入面试流程，无法取消签到"),
    /** 当前面试场次已经签到。 */
    CHECK_IN_ALREADY_EXISTS(140035, "已经签到，请勿重复签到"),
    /** 海报列表已被其他操作修改，需要刷新后重新提交顺序。 */
    POSTER_ORDER_CONFLICT(140036, "海报列表已变化，请刷新后重试"),
    /** 已在本部门其他已发布场次签到，同一部门同时只能排一队。 */
    CHECK_IN_OTHER_SESSION_EXISTS(
            140037, "已在本部门其他面试场次签到，请先取消后再签到"),
    /** 部门尚未配置报名问卷，不允许报名。 */
    QUESTIONNAIRE_NOT_CONFIGURED(140038, "该部门尚未开放报名"),

    // ---------- 15xxxx 外部依赖错误 ----------
    /** 第三方服务不可用。 */
    THIRD_PARTY_UNAVAILABLE(150001, "第三方服务不可用"),
    /** 第三方接口超时。 */
    THIRD_PARTY_TIMEOUT(150002, "第三方接口超时"),
    /** 第三方返回异常。 */
    THIRD_PARTY_BAD_RESPONSE(150003, "第三方返回异常"),

    // ---------- 平台系统级错误 ----------
    /** 系统内部异常。 */
    SYSTEM_ERROR(-1, "服务器错误"),
    /** 未捕获异常。 */
    UNKNOWN_ERROR(-2, "未知错误");

    /** 数字状态码。 */
    private final int code;

    /** 默认提示文案。 */
    private final String msg;

    BizCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
