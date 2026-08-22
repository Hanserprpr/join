package cn.sduonline.join.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminOrganizationService {

    private final AdminOrganizationMapper organizationMapper;

    @Transactional
    public ServiceResult<BoardVO> createBoard(BoardCreateRequest request) {
        Board board = new Board();
        board.setName(request.name().trim());
        board.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        board.setEnabled(request.enabled() == null || request.enabled());
        organizationMapper.insertBoard(board);
        return ServiceResult.success(BoardVO.from(board));
    }

    @Transactional
    public ServiceResult<WorkstationVO> createWorkstation(WorkstationCreateRequest request) {
        if (organizationMapper.countEnabledBoard(request.boardId()) == 0) {
            return ServiceResult.failure(BizCode.BOARD_NOT_FOUND);
        }

        Workstation workstation = new Workstation();
        workstation.setBoardId(request.boardId());
        workstation.setName(request.name().trim());
        workstation.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        workstation.setEnabled(request.enabled() == null || request.enabled());
        organizationMapper.insertWorkstation(workstation);
        return ServiceResult.success(WorkstationVO.from(workstation));
    }

    @Transactional
    public ServiceResult<DepartmentVO> createDepartment(DepartmentCreateRequest request) {
        if (organizationMapper.countEnabledWorkstation(request.workstationId()) == 0) {
            return ServiceResult.failure(BizCode.WORKSTATION_NOT_FOUND);
        }

        Department department = new Department();
        department.setWorkstationId(request.workstationId());
        department.setName(request.name().trim());
        department.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        department.setEnabled(request.enabled() == null || request.enabled());
        department.setQrCheckInEnabled(false);

        organizationMapper.insertDepartment(department);
        return ServiceResult.success(DepartmentVO.from(department));
    }

    @Transactional
    public ServiceResult<Void> deleteBoard(Long boardId) {
        if (organizationMapper.countBoard(boardId) == 0) {
            return ServiceResult.failure(BizCode.BOARD_NOT_FOUND);
        }

        for (Long workstationId : organizationMapper.selectWorkstationIdsByBoard(boardId)) {
            deleteWorkstationCascade(workstationId);
        }
        organizationMapper.deleteRoleScopesByScope("BOARD", boardId);
        organizationMapper.deleteBoardById(boardId);
        return ServiceResult.success(null);
    }

    @Transactional
    public ServiceResult<Void> deleteWorkstation(Long workstationId) {
        if (organizationMapper.countWorkstation(workstationId) == 0) {
            return ServiceResult.failure(BizCode.WORKSTATION_NOT_FOUND);
        }

        deleteWorkstationCascade(workstationId);
        return ServiceResult.success(null);
    }

    @Transactional
    public ServiceResult<Void> deleteDepartment(Long departmentId) {
        if (organizationMapper.countDepartment(departmentId) == 0) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }

        deleteDepartmentCascade(departmentId);
        return ServiceResult.success(null);
    }

    private void deleteWorkstationCascade(Long workstationId) {
        for (Long departmentId : organizationMapper.selectDepartmentIdsByWorkstation(workstationId)) {
            deleteDepartmentCascade(departmentId);
        }
        organizationMapper.deleteRoleScopesByScope("WORKSTATION", workstationId);
        organizationMapper.deleteWorkstationById(workstationId);
    }

    /**
     * 按外键依赖顺序清理部门下的报名、面试、签到等数据；
     * department_poster/department_question 等表在数据库层已配置 ON DELETE CASCADE，
     * 随 department 行删除自动清理，此处无需重复处理。
     */
    private void deleteDepartmentCascade(Long departmentId) {
        organizationMapper.deleteInterviewsByDepartment(departmentId);
        organizationMapper.deleteInterviewCarryoversByDepartment(departmentId);
        organizationMapper.deleteCheckInSequencesByDepartment(departmentId);
        organizationMapper.deleteCheckInsByDepartment(departmentId);
        organizationMapper.deleteInterviewRoomsByDepartment(departmentId);
        organizationMapper.deleteAdmissionEmailOutboxByDepartment(departmentId);
        organizationMapper.deleteApplicationsByDepartment(departmentId);
        organizationMapper.deleteInterviewSessionsByDepartment(departmentId);
        organizationMapper.deleteRoleScopesByScope("DEPARTMENT", departmentId);
        organizationMapper.deleteDepartmentById(departmentId);
    }
}
