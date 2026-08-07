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
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门详情查询与维护服务
 */
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentQuestionnaireMapper questionnaireMapper;
    private final AuthorizationService authorizationService;

    /** canManage 显示层短缓存（30 秒）；操作层权限校验仍实时走 AOP，不受缓存影响。 */
    private static final long CAN_MANAGE_CACHE_TTL_MILLIS = 30_000L;
    private final Map<String, CachedCanManage> canManageCache =
            new ConcurrentHashMap<>();

    /**
     * 查询部门详情和海报
     *
     * @param departmentId 部门 ID
     * @param casId 当前用户学号，未登录时传 {@code null}
     * @return 部门详情查询结果
     */
    public ServiceResult<DepartmentVO> findById(Long departmentId, String casId) {
        Department department = organizationMapper.selectDepartmentById(departmentId);
        if (department == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(toVO(department, casId));
    }

    /**
     * 完整更新部门详情和海报
     *
     * @param departmentId 部门 ID
     * @param casId 当前用户学号
     * @param request 完整更新参数
     * @return 更新结果
     */
    @Transactional
    public ServiceResult<DepartmentVO> updateDetail(
            Long departmentId,
            String casId,
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
        return ServiceResult.success(toVO(department, casId));
    }

    /**
     * 部分更新部门详情和海报
     *
     * @param departmentId 部门 ID
     * @param casId 当前用户学号
     * @param request 部分更新参数
     * @return 更新结果
     */
    @Transactional
    public ServiceResult<DepartmentVO> patchDetail(
            Long departmentId,
            String casId,
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
        return ServiceResult.success(toVO(department, casId));
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

    private DepartmentVO toVO(Department department, String casId) {
        List<DepartmentPosterVO> posters = organizationMapper
                .selectDepartmentPosters(department.getId())
                .stream()
                .map(DepartmentPosterVO::from)
                .toList();
        return DepartmentVO.from(
                department,
                posters,
                questionnaireMapper.countQuestions(department.getId()) > 0,
                canManage(casId, department.getId())
        );
    }

    private boolean canManage(String casId, Long departmentId) {
        if (casId == null) {
            return false;
        }
        String key = casId + ":" + departmentId;
        long now = System.currentTimeMillis();
        CachedCanManage cached = canManageCache.get(key);
        if (cached != null && cached.expiresAt() > now) {
            return cached.value();
        }
        boolean value = authorizationService.canAccessWithPermission(
                casId,
                PermissionCode.RECRUITMENT_MANAGE.code(),
                OrgType.DEPARTMENT,
                departmentId
        );
        canManageCache.put(
                key, new CachedCanManage(value, now + CAN_MANAGE_CACHE_TTL_MILLIS)
        );
        return value;
    }

    private record CachedCanManage(boolean value, long expiresAt) {}
}
