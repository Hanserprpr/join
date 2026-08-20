package cn.sduonline.join.mapper;

import cn.sduonline.join.data.po.Banner;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 轮播图数据访问
 */
@Mapper
public interface BannerMapper {

    @Select("""
            SELECT id, url, route
            FROM banner
            ORDER BY id ASC
            """)
    List<Banner> selectAll();
}
