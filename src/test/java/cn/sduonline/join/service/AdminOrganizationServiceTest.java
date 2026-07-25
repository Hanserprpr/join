package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.BoardCreateRequest;
import cn.sduonline.join.data.dto.BoardVO;
import cn.sduonline.join.data.dto.DepartmentCreateRequest;
import cn.sduonline.join.data.dto.DepartmentVO;
import cn.sduonline.join.data.dto.WorkstationCreateRequest;
import cn.sduonline.join.data.dto.WorkstationVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.Board;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.Workstation;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminOrganizationServiceTest {

    @Mock
    private AdminOrganizationMapper organizationMapper;

    private AdminOrganizationService service;

    @BeforeEach
    void setUp() {
        service = new AdminOrganizationService(organizationMapper);
    }

    @Test
    void createBoardCreatesBoard() {
        when(organizationMapper.insertBoard(any())).thenAnswer(invocation -> {
            Board board = invocation.getArgument(0);
            board.setId(1L);
            return 1;
        });

        ServiceResult<BoardVO> result = service.createBoard(
                new BoardCreateRequest("技术板块", null, null)
        );

        assertTrue(result.isSuccess());
        assertEquals(1L, result.data().id());
        assertEquals(0, result.data().sortOrder());
        assertTrue(result.data().enabled());
    }

    @Test
    void createWorkstationCreatesWorkstation() {
        when(organizationMapper.countEnabledBoard(1L)).thenReturn(1L);
        when(organizationMapper.insertWorkstation(any())).thenAnswer(invocation -> {
            Workstation workstation = invocation.getArgument(0);
            workstation.setId(5L);
            return 1;
        });

        ServiceResult<WorkstationVO> result = service.createWorkstation(
                new WorkstationCreateRequest(1L, "开发工作站", null, null)
        );

        assertTrue(result.isSuccess());
        assertEquals(5L, result.data().id());
        assertEquals(1L, result.data().boardId());
    }

    @Test
    void createWorkstationRejectsMissingBoard() {
        when(organizationMapper.countEnabledBoard(1L)).thenReturn(0L);

        ServiceResult<WorkstationVO> result = service.createWorkstation(
                new WorkstationCreateRequest(1L, "开发工作站", null, null)
        );

        assertEquals(BizCode.BOARD_NOT_FOUND, result.error());
        verify(organizationMapper, never()).insertWorkstation(any());
    }

    @Test
    void createDepartmentCreatesDepartment() {
        when(organizationMapper.countEnabledWorkstation(5L)).thenReturn(1L);
        when(organizationMapper.insertDepartment(any())).thenAnswer(invocation -> {
            Department department = invocation.getArgument(0);
            department.setId(12L);
            return 1;
        });

        ServiceResult<DepartmentVO> result = service.createDepartment(request());

        assertTrue(result.isSuccess());
        assertEquals(12L, result.data().id());
        assertEquals(0, result.data().sortOrder());
        assertTrue(result.data().enabled());
    }

    @Test
    void createDepartmentRejectsMissingWorkstation() {
        when(organizationMapper.countEnabledWorkstation(5L)).thenReturn(0L);

        ServiceResult<DepartmentVO> result = service.createDepartment(request());

        assertEquals(BizCode.WORKSTATION_NOT_FOUND, result.error());
        verify(organizationMapper, never()).insertDepartment(any());
    }

    private static DepartmentCreateRequest request() {
        return new DepartmentCreateRequest(5L, "后端部门", null, null);
    }
}
