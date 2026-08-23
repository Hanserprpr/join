package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.OrganizationTreeRow;
import cn.sduonline.join.data.enums.Campus;
import cn.sduonline.join.data.po.Board;
import cn.sduonline.join.data.po.Workstation;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private AdminOrganizationMapper organizationMapper;

    private BoardService service;

    @BeforeEach
    void setUp() {
        service = new BoardService(organizationMapper);
    }

    @Test
    void findEnabledBoardsMapsBoardList() {
        Board board = new Board();
        board.setId(1L);
        board.setName("技术板块");
        board.setSortOrder(0);
        board.setEnabled(true);
        Workstation workstation = new Workstation();
        workstation.setId(5L);
        workstation.setBoardId(1L);
        workstation.setName("开发工作站");
        workstation.setSortOrder(0);
        workstation.setEnabled(true);
        when(organizationMapper.selectEnabledBoards()).thenReturn(List.of(board));
        when(organizationMapper.selectEnabledWorkstationsByBoard(1L))
                .thenReturn(List.of(workstation));

        var result = service.findEnabledBoards();

        assertEquals(1, result.size());
        assertEquals("技术板块", result.getFirst().name());
        assertEquals(5L, result.getFirst().workstations().getFirst().id());
        assertEquals(
                "开发工作站",
                result.getFirst().workstations().getFirst().name()
        );
    }

    @Test
    void organizationTreeContainsDepartmentsBelowEachWorkstation() {
        when(organizationMapper.selectEnabledOrganizationTree()).thenReturn(List.of(
                new OrganizationTreeRow(
                        1L, "技术板块", 5L, "开发工作站",
                        12L, "后端部门", Campus.SOFTWARE_PARK, 88L,
                        "负责学生在线服务端架构与开发"
                ),
                new OrganizationTreeRow(
                        1L, "技术板块", 5L, "开发工作站",
                        13L, "前端部门", Campus.CENTRAL, null,
                        "负责学生在线前端产品开发"
                ),
                new OrganizationTreeRow(
                        2L, "运营板块", null, null,
                        null, null, null, null, null
                )
        ));

        var result = service.findEnabledOrganizationTree();

        assertEquals(2, result.size());
        assertEquals("技术板块", result.getFirst().name());
        assertEquals(1, result.getFirst().workstations().size());
        assertEquals(2, result.getFirst().workstations()
                .getFirst().departments().size());
        assertEquals("后端部门", result.getFirst().workstations()
                .getFirst().departments().getFirst().name());
        assertEquals(Campus.SOFTWARE_PARK, result.getFirst().workstations()
                .getFirst().departments().getFirst().campus());
        assertEquals(88L, result.getFirst().workstations()
                .getFirst().departments().getFirst().assetId());
        assertEquals("负责学生在线服务端架构与开发", result.getFirst()
                .workstations().getFirst().departments().getFirst()
                .introduction());
        assertTrue(result.get(1).workstations().isEmpty());
    }
}
