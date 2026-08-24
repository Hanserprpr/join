package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
}
