package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DepartmentSummaryVO;
import cn.sduonline.join.data.dto.WorkstationDetailVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.Workstation;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作站公开查询服务
 */
@Service
@RequiredArgsConstructor
public class WorkstationService {

    private final AdminOrganizationMapper organizationMapper;

    /**
     * 查询已启用工作站及其已启用部门
     *
     * @param workstationId 工作站 ID
     * @return 工作站详情查询结果
     */
    @Transactional(readOnly = true)
    public ServiceResult<WorkstationDetailVO> findById(Long workstationId) {
        Workstation workstation =
                organizationMapper.selectEnabledWorkstationById(workstationId);
        if (workstation == null) {
            return ServiceResult.failure(BizCode.WORKSTATION_NOT_FOUND);
        }
        var departments = organizationMapper
                .selectEnabledDepartmentsByWorkstation(workstationId)
                .stream()
                .map(DepartmentSummaryVO::from)
                .toList();
        return ServiceResult.success(
                WorkstationDetailVO.from(workstation, departments)
        );
    }
}
