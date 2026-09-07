package cn.sduonline.join.mapper;

import cn.sduonline.join.data.dto.StudentAcademicProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 复用当前数据源，只读同一 MySQL 实例中的基础用户库。 */
@Mapper
public interface StudentAcademicProfileMapper {

    /** 按统一认证账号找到基础用户 ID，再关联学院和专业。 */
    @Select("""
            SELECT u.depart AS college, u.major AS major
            FROM isdusvr_db.isdu_basic_cas c
            INNER JOIN isdusvr_db.isdu_basic_user u ON u.id = c.id
            WHERE c.cas_id = #{casId}
            """)
    @Options(timeout = 3)
    StudentAcademicProfile selectByCasId(@Param("casId") String casId);
}
