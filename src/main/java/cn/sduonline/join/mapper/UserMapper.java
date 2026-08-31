package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 显式将微信 OpenID 更新为 NULL。
     * MyBatis-Plus 的 updateById 默认忽略 null 字段，解绑不能使用实体更新。
     */
    @Update("""
            UPDATE `user`
            SET wechat_openid = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE cas_id = #{casId}
            """)
    int clearWechatOpenid(@Param("casId") String casId);

    /**
     * 统计符合筛选条件的用户总数，条件与 {@link #selectUsers} 保持一致。
     * <p>
     * keyword 由调用方转义后传入，SQL 里统一声明 {@code ESCAPE '!'}。
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM `user`
            <where>
              <if test="keyword != null and keyword != ''">
                AND (name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR cas_id LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR phone LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR email LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR qq LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!')
              </if>
              <if test="college != null and college != ''">
                AND college = #{college}
              </if>
              <if test="major != null and major != ''">
                AND major = #{major}
              </if>
              <if test="grade != null">
                AND grade = #{grade}
              </if>
              <if test="profileCompleted != null">
                AND profile_completed = #{profileCompleted}
              </if>
              <if test="wechatBound != null and wechatBound">
                AND wechat_openid IS NOT NULL
              </if>
              <if test="wechatBound != null and wechatBound == false">
                AND wechat_openid IS NULL
              </if>
            </where>
            </script>
            """)
    long countUsers(
            @Param("keyword") String keyword,
            @Param("college") String college,
            @Param("major") String major,
            @Param("grade") Integer grade,
            @Param("profileCompleted") Boolean profileCompleted,
            @Param("wechatBound") Boolean wechatBound
    );

    /**
     * 按筛选条件分页查询用户。
     * <p>
     * 排序字段与方向由 {@code choose} 白名单拼接，调用方传入的值不会进入 SQL；
     * 每种排序都以 cas_id 兜底，保证分页结果稳定不重复、不遗漏。
     */
    @Select("""
            <script>
            SELECT cas_id, sub, name, email, phone, avatar_key, wechat_openid,
                   profile_completed, qq, college, major, grade,
                   created_at, updated_at
            FROM `user`
            <where>
              <if test="keyword != null and keyword != ''">
                AND (name LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR cas_id LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR phone LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR email LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!'
                     OR qq LIKE CONCAT('%', #{keyword}, '%') ESCAPE '!')
              </if>
              <if test="college != null and college != ''">
                AND college = #{college}
              </if>
              <if test="major != null and major != ''">
                AND major = #{major}
              </if>
              <if test="grade != null">
                AND grade = #{grade}
              </if>
              <if test="profileCompleted != null">
                AND profile_completed = #{profileCompleted}
              </if>
              <if test="wechatBound != null and wechatBound">
                AND wechat_openid IS NOT NULL
              </if>
              <if test="wechatBound != null and wechatBound == false">
                AND wechat_openid IS NULL
              </if>
            </where>
            <choose>
              <when test="sortBy == 'casId' and sortOrder == 'asc'">
                ORDER BY cas_id ASC
              </when>
              <when test="sortBy == 'casId'">
                ORDER BY cas_id DESC
              </when>
              <when test="sortBy == 'grade' and sortOrder == 'asc'">
                ORDER BY grade IS NULL ASC, grade ASC, cas_id ASC
              </when>
              <when test="sortBy == 'grade'">
                ORDER BY grade IS NULL ASC, grade DESC, cas_id ASC
              </when>
              <when test="sortOrder == 'asc'">
                ORDER BY created_at ASC, cas_id ASC
              </when>
              <otherwise>
                ORDER BY created_at DESC, cas_id ASC
              </otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<User> selectUsers(
            @Param("keyword") String keyword,
            @Param("college") String college,
            @Param("major") String major,
            @Param("grade") Integer grade,
            @Param("profileCompleted") Boolean profileCompleted,
            @Param("wechatBound") Boolean wechatBound,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("offset") int offset,
            @Param("limit") int limit
    );
}
