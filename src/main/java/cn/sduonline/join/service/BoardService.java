package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.BoardWithWorkstationsVO;
import cn.sduonline.join.data.dto.WorkstationSummaryVO;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 板块公开查询服务
 */
@Service
@RequiredArgsConstructor
public class BoardService {

    private final AdminOrganizationMapper organizationMapper;

    /**
     * 查询已启用板块及其已启用工作站
     *
     * @return 板块和工作站列表
     */
    @Transactional(readOnly = true)
    public List<BoardWithWorkstationsVO> findEnabledBoards() {
        return organizationMapper.selectEnabledBoards()
                .stream()
                .map(board -> BoardWithWorkstationsVO.from(
                        board,
                        organizationMapper
                                .selectEnabledWorkstationsByBoard(board.getId())
                                .stream()
                                .map(WorkstationSummaryVO::from)
                                .toList()
                ))
                .toList();
    }
}
