package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DepartmentDetailUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentDetailPatchRequest;
import cn.sduonline.join.data.dto.DepartmentVO;
import cn.sduonline.join.data.dto.DepartmentPosterRequest;
import cn.sduonline.join.data.dto.DepartmentPosterVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentPoster;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 部门详情查询与维护服务
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentQuestionnaireMapper questionnaireMapper;

    /**
     * 查询部门详情和海报
     *
     * @param departmentId 部门 ID
     * @return 部门详情查询结果
     */
    public ServiceResult<DepartmentVO> findById(Long departmentId) {
        Department department = organizationMapper.selectDepartmentById(departmentId);
        if (department == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(toVO(department));
    }

    /**
     * 完整更新部门详情和海报
     *
     * @param departmentId 部门 ID
     * @param request 完整更新参数
     * @return 更新结果
     */
    @Transactional
    public ServiceResult<DepartmentVO> updateDetail(
            Long departmentId,
            DepartmentDetailUpdateRequest request
    ) {
        Department department = organizationMapper.selectDepartmentById(departmentId);
        if (department == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }

        department.setCampus(request.campus());
        department.setIntroduction(trimToNull(request.introduction()));
        department.setAchievements(trimToNull(request.achievements()));
        department.setRecruitmentRequirements(
                trimToNull(request.recruitmentRequirements())
        );
        department.setContact(trimToNull(request.contact()));
        department.setRecruitmentGroup(trimToNull(request.recruitmentGroup()));
        organizationMapper.updateDepartmentDetail(department);
        replacePosters(departmentId, request.posters());
        return ServiceResult.success(toVO(department));
    }

    /**
     * 部分更新部门详情和海报
     *
     * @param departmentId 部门 ID
     * @param request 部分更新参数
     * @return 更新结果
     */
    @Transactional
    public ServiceResult<DepartmentVO> patchDetail(
            Long departmentId,
            DepartmentDetailPatchRequest request
    ) {
        Department department = organizationMapper.selectDepartmentById(departmentId);
        if (department == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }

        if (request.isCampusPresent()) {
            department.setCampus(request.getCampus());
        }
        if (request.isIntroductionPresent()) {
            department.setIntroduction(trimToNull(request.getIntroduction()));
        }
        if (request.isAchievementsPresent()) {
            department.setAchievements(trimToNull(request.getAchievements()));
        }
        if (request.isRecruitmentRequirementsPresent()) {
            department.setRecruitmentRequirements(
                    trimToNull(request.getRecruitmentRequirements())
            );
        }
        if (request.isContactPresent()) {
            department.setContact(trimToNull(request.getContact()));
        }
        if (request.isRecruitmentGroupPresent()) {
            department.setRecruitmentGroup(
                    trimToNull(request.getRecruitmentGroup())
            );
        }
        organizationMapper.updateDepartmentDetail(department);
        if (request.isPostersPresent()) {
            replacePosters(departmentId, request.getPosters());
        }
        return ServiceResult.success(toVO(department));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void replacePosters(
            Long departmentId,
            List<DepartmentPosterRequest> requests
    ) {
        organizationMapper.deleteDepartmentPosters(departmentId);
        if (requests == null) {
            return;
        }
        for (int index = 0; index < requests.size(); index++) {
            DepartmentPosterRequest request = requests.get(index);
            DepartmentPoster poster = new DepartmentPoster();
            poster.setDepartmentId(departmentId);
            poster.setUrl(request.url().trim());
            poster.setSortOrder(
                    request.sortOrder() == null ? index : request.sortOrder()
            );
            organizationMapper.insertDepartmentPoster(poster);
        }
    }

    private DepartmentVO toVO(Department department) {
        List<DepartmentPosterVO> posters = organizationMapper
                .selectDepartmentPosters(department.getId())
                .stream()
                .map(DepartmentPosterVO::from)
                .toList();
        return DepartmentVO.from(
                department,
                posters,
                questionnaireMapper.countQuestions(department.getId()) > 0
        );
    }
}
