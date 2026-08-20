package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.BannerVO;
import cn.sduonline.join.mapper.BannerMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 轮播图公开查询服务
 */
@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerMapper bannerMapper;

    /**
     * 查询全部轮播图（按插入顺序）
     *
     * @return 轮播图列表
     */
    @Transactional(readOnly = true)
    public List<BannerVO> findAll() {
        return bannerMapper.selectAll()
                .stream()
                .map(BannerVO::from)
                .toList();
    }
}
