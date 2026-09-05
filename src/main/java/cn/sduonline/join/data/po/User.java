package cn.sduonline.join.data.po;

import cn.sduonline.join.data.enums.Campus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户表实体（学号主身份 + OIDC 身份 + 学生资料）。
 */
@Data
@TableName("user")
public class User {

    /**
     * OIDC subject；外部 Token 首次创建的用户可能暂时为空。
     */
    private String sub;

    /**
     * 姓名 / 展示名。
     */
    private String name;

    /**
     * 统一认证账号（学号/工号），主键，对应 OIDC claim {@code casID}。
     */
    @TableId(value = "cas_id", type = IdType.INPUT)
    private String casId;

    /**
     * 邮箱，选填，允许用户清空。
     * <p>
     * {@link FieldStrategy#ALWAYS} 让该列始终参与 {@code updateById}：
     * 默认的 {@code NOT_NULL} 策略会把值为 null 的列从 UPDATE 语句里剔除，
     * 清空就只改内存、不落库。所有 {@code updateById(user)} 的调用方都先
     * {@code selectById} 读出整行再改，因此始终写回不会覆盖他人的值；
     * 新增调用方若要用半填充的实体更新，必须避开 {@code updateById}。
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 头像在文件存储中的对象 key；不保存域名，便于切换 CDN 或存储服务。
     */
    private String avatarKey;

    /**
     * 当前公众号下的 OpenID，用于发送模板消息。
     */
    private String wechatOpenid;

    /** 报名必填资料是否已完成。 */
    private Boolean profileCompleted;

    /** QQ 号，选填，允许用户清空；策略同 {@link #email}。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String qq;

    /**
     * 学院。
     */
    private String college;

    /** 学生所在校区；历史资料允许为空。 */
    private Campus campus;

    /**
     * 专业。
     */
    private String major;

    /**
     * 入学年级，如 2024。
     */
    private Integer grade;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;
}
