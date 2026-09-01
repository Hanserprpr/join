package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.BoardWithWorkstationsVO;
import cn.sduonline.join.data.dto.OrganizationDepartmentVO;
import cn.sduonline.join.data.dto.OrganizationTreeRow;
import cn.sduonline.join.data.dto.OrganizationTreeVO;
import cn.sduonline.join.data.dto.OrganizationWorkstationVO;
import cn.sduonline.join.data.dto.WorkstationSummaryVO;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import java.util.LinkedHashMap;
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

    /**
     * 查询全部启用的板块、工作站、部门三级组织树。
     *
     * @return 板块、工作站、部门三级组织树
     */
    @Transactional(readOnly = true)
    public List<OrganizationTreeVO> findEnabledOrganizationTree() {
        LinkedHashMap<Long, BoardAccumulator> boards = new LinkedHashMap<>();
        for (OrganizationTreeRow row : organizationMapper
                .selectEnabledOrganizationTree()) {
            BoardAccumulator board = boards.computeIfAbsent(
                    row.boardId(),
                    id -> new BoardAccumulator(
                            row.boardName(), new LinkedHashMap<>()
                    )
            );
            if (row.workstationId() == null) {
                continue;
            }
            WorkstationAccumulator workstation = board.workstations()
                    .computeIfAbsent(
                            row.workstationId(),
                            id -> new WorkstationAccumulator(
                                    row.workstationName(), new LinkedHashMap<>()
                            )
                    );
            if (row.departmentId() != null) {
                workstation.departments().putIfAbsent(
                        row.departmentId(),
                        new OrganizationDepartmentVO(
                                row.departmentId(),
                                row.departmentName(),
                                row.departmentCampuses() == null
                                        ? List.of() : row.departmentCampuses(),
                                row.departmentAssetId(),
                                row.departmentIntroduction()
                        )
                );
            }
        }

        return boards.entrySet().stream()
                .map(board -> new OrganizationTreeVO(
                        board.getKey(),
                        board.getValue().name(),
                        board.getValue().workstations().entrySet().stream()
                                .map(workstation -> new OrganizationWorkstationVO(
                                        workstation.getKey(),
                                        workstation.getValue().name(),
                                        List.copyOf(
                                                workstation.getValue()
                                                        .departments().values()
                                        )
                                ))
                                .toList()
                ))
                .toList();
    }

    private record BoardAccumulator(
            String name,
            LinkedHashMap<Long, WorkstationAccumulator> workstations
    ) {
    }

    private record WorkstationAccumulator(
            String name,
            LinkedHashMap<Long, OrganizationDepartmentVO> departments
    ) {
    }
}
