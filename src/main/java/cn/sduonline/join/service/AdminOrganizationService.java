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

        organizationMapper.insertDepartment(department);
        return ServiceResult.success(DepartmentVO.from(department));
    }
}
