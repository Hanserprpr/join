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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
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

    @Test
    void deleteDepartmentRejectsMissingDepartment() {
        when(organizationMapper.countDepartment(12L)).thenReturn(0L);

        ServiceResult<Void> result = service.deleteDepartment(12L);

        assertEquals(BizCode.DEPARTMENT_NOT_FOUND, result.error());
        verify(organizationMapper, never()).deleteDepartmentById(any());
    }

    @Test
    void deleteDepartmentCascadesInForeignKeyOrder() {
        when(organizationMapper.countDepartment(12L)).thenReturn(1L);

        ServiceResult<Void> result = service.deleteDepartment(12L);

        assertTrue(result.isSuccess());
        InOrder order = Mockito.inOrder(organizationMapper);
        order.verify(organizationMapper).deleteInterviewsByDepartment(12L);
        order.verify(organizationMapper).deleteInterviewCarryoversByDepartment(12L);
        order.verify(organizationMapper).deleteCheckInSequencesByDepartment(12L);
        order.verify(organizationMapper).deleteCheckInsByDepartment(12L);
        order.verify(organizationMapper).deleteInterviewRoomsByDepartment(12L);
        order.verify(organizationMapper).deleteAdmissionEmailOutboxByDepartment(12L);
        order.verify(organizationMapper).deleteApplicationsByDepartment(12L);
        order.verify(organizationMapper).deleteInterviewSessionsByDepartment(12L);
        order.verify(organizationMapper).deleteRoleScopesByScope("DEPARTMENT", 12L);
        order.verify(organizationMapper).deleteDepartmentById(12L);
    }

    @Test
    void deleteWorkstationRejectsMissingWorkstation() {
        when(organizationMapper.countWorkstation(5L)).thenReturn(0L);

        ServiceResult<Void> result = service.deleteWorkstation(5L);

        assertEquals(BizCode.WORKSTATION_NOT_FOUND, result.error());
        verify(organizationMapper, never()).deleteWorkstationById(any());
    }

    @Test
    void deleteWorkstationCascadesToItsDepartments() {
        when(organizationMapper.countWorkstation(5L)).thenReturn(1L);
        when(organizationMapper.selectDepartmentIdsByWorkstation(5L))
                .thenReturn(List.of(12L, 13L));

        ServiceResult<Void> result = service.deleteWorkstation(5L);

        assertTrue(result.isSuccess());
        verify(organizationMapper).deleteDepartmentById(12L);
        verify(organizationMapper).deleteDepartmentById(13L);
        InOrder order = Mockito.inOrder(organizationMapper);
        order.verify(organizationMapper).deleteDepartmentById(13L);
        order.verify(organizationMapper).deleteRoleScopesByScope("WORKSTATION", 5L);
        order.verify(organizationMapper).deleteWorkstationById(5L);
    }

    @Test
    void deleteBoardRejectsMissingBoard() {
        when(organizationMapper.countBoard(1L)).thenReturn(0L);

        ServiceResult<Void> result = service.deleteBoard(1L);

        assertEquals(BizCode.BOARD_NOT_FOUND, result.error());
        verify(organizationMapper, never()).deleteBoardById(any());
    }

    @Test
    void deleteBoardCascadesToItsWorkstationsAndDepartments() {
        when(organizationMapper.countBoard(1L)).thenReturn(1L);
        when(organizationMapper.selectWorkstationIdsByBoard(1L)).thenReturn(List.of(5L));
        when(organizationMapper.selectDepartmentIdsByWorkstation(5L)).thenReturn(List.of(12L));

        ServiceResult<Void> result = service.deleteBoard(1L);

        assertTrue(result.isSuccess());
        InOrder order = Mockito.inOrder(organizationMapper);
        order.verify(organizationMapper).deleteDepartmentById(12L);
        order.verify(organizationMapper).deleteWorkstationById(5L);
        order.verify(organizationMapper).deleteRoleScopesByScope("BOARD", 1L);
        order.verify(organizationMapper).deleteBoardById(1L);
    }

    private static DepartmentCreateRequest request() {
        return new DepartmentCreateRequest(5L, "后端部门", null, null);
    }
}
