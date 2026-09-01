package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DepartmentAchievementRequest;
import cn.sduonline.join.data.dto.DepartmentAchievementVO;
import cn.sduonline.join.data.dto.DepartmentDetailUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentDetailPatchRequest;
import cn.sduonline.join.data.dto.DepartmentVO;
import cn.sduonline.join.data.dto.DepartmentPosterOrderItemRequest;
import cn.sduonline.join.data.dto.DepartmentPosterOrderUpdateRequest;
import cn.sduonline.join.data.dto.DepartmentPosterRequest;
import cn.sduonline.join.data.dto.DepartmentPosterVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentAchievement;
import cn.sduonline.join.data.po.DepartmentPoster;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentQuestionnaireMapper;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.poster.PosterUrlPolicy;
import cn.sduonline.join.service.poster.PosterStorage;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final PosterUrlPolicy posterUrlPolicy;
    private final PosterStorage posterStorage;

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
        posterUrlPolicy.validateAll(request.posters());
        BizCode posterConflict = validatePosterReferences(
                departmentId, request.posters()
        );
        if (posterConflict != null) {
            return ServiceResult.failure(posterConflict);
        }

        department.setCampuses(request.campuses());
        department.setIntroduction(trimToNull(request.introduction()));
        department.setRecruitmentRequirements(
                trimToNull(request.recruitmentRequirements())
        );
        department.setContact(trimToNull(request.contact()));
        department.setRecruitmentGroup(trimToNull(request.recruitmentGroup()));
        organizationMapper.updateDepartmentDetail(department);
        applyPosters(departmentId, request.posters());
        replaceAchievements(departmentId, request.achievements());
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
        if (request.isPostersPresent()) {
            posterUrlPolicy.validateAll(request.getPosters());
            BizCode posterConflict = validatePosterReferences(
                    departmentId, request.getPosters()
            );
            if (posterConflict != null) {
                return ServiceResult.failure(posterConflict);
            }
        }

        if (request.isCampusesPresent()) {
            department.setCampuses(request.getCampuses());
        }
        if (request.isIntroductionPresent()) {
            department.setIntroduction(trimToNull(request.getIntroduction()));
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
            applyPosters(departmentId, request.getPosters());
        }
        if (request.isAchievementsPresent()) {
            replaceAchievements(departmentId, request.getAchievements());
        }
        return ServiceResult.success(toVO(department, casId));
    }

    /**
     * 原子更新部门全部海报的展示顺序。
     * 请求必须覆盖当前全部海报，且排序值恰好为 {@code 0..n-1}。
     *
     * @param departmentId 部门 ID
     * @param request 全部海报的目标顺序
     * @return 更新后按新顺序排列的海报
     */
    @Transactional
    public ServiceResult<List<DepartmentPosterVO>> reorderPosters(
            Long departmentId,
            DepartmentPosterOrderUpdateRequest request
    ) {
        Department department = organizationMapper
                .selectDepartmentByIdForUpdate(departmentId);
        if (department == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }

        List<DepartmentPoster> currentPosters = organizationMapper
                .selectDepartmentPostersForUpdate(departmentId);
        List<DepartmentPosterOrderItemRequest> requestedPosters =
                request == null ? null : request.posters();
        if (!hasValidCompleteOrder(requestedPosters)) {
            return ServiceResult.failure(BizCode.PARAM_INVALID);
        }

        Set<Long> currentIds = new HashSet<>();
        for (DepartmentPoster poster : currentPosters) {
            currentIds.add(poster.getId());
        }
        Set<Long> requestedIds = new HashSet<>();
        for (DepartmentPosterOrderItemRequest poster : requestedPosters) {
            requestedIds.add(poster.id());
        }
        if (!currentIds.equals(requestedIds)) {
            return ServiceResult.failure(BizCode.POSTER_ORDER_CONFLICT);
        }

        requestedPosters.stream()
                .sorted(Comparator.comparing(DepartmentPosterOrderItemRequest::id))
                .forEach(poster -> organizationMapper.updateDepartmentPosterSortOrder(
                        departmentId, poster.id(), poster.sortOrder()
                ));

        return ServiceResult.success(toPosterVOs(
                organizationMapper.selectDepartmentPosters(departmentId)
        ));
    }

    private static boolean hasValidCompleteOrder(
            List<DepartmentPosterOrderItemRequest> posters
    ) {
        if (posters == null || posters.size() > 20) {
            return false;
        }
        Set<Long> ids = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (DepartmentPosterOrderItemRequest poster : posters) {
            if (poster == null || poster.id() == null || poster.id() <= 0
                    || poster.sortOrder() == null || poster.sortOrder() < 0
                    || !ids.add(poster.id())
                    || !sortOrders.add(poster.sortOrder())) {
                return false;
            }
        }
        for (int expected = 0; expected < posters.size(); expected++) {
            if (!sortOrders.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 校验请求引用的海报 ID 都属于该部门且没有重复，并锁定当前海报行。
     * 必须在任何写操作之前调用，避免冲突返回时留下已提交的部分更新。
     *
     * @return 校验失败的业务码，通过时返回 {@code null}
     */
    private BizCode validatePosterReferences(
            Long departmentId,
            List<DepartmentPosterRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        Set<Long> currentIds = new HashSet<>();
        for (DepartmentPoster poster
                : organizationMapper.selectDepartmentPostersForUpdate(departmentId)) {
            currentIds.add(poster.getId());
        }
        Set<Long> referencedIds = new HashSet<>();
        for (DepartmentPosterRequest request : requests) {
            if (request == null || request.id() == null) {
                continue;
            }
            if (!currentIds.contains(request.id())
                    || !referencedIds.add(request.id())) {
                return BizCode.POSTER_ORDER_CONFLICT;
            }
        }
        return null;
    }

    /**
     * 按目标列表增量更新海报：带 id 的保留并调整顺序，只带 url 的新增，
     * 当前存在但未出现在目标列表中的删除。排序按请求数组下标，显式传入时以其为准。
     */
    private void applyPosters(
            Long departmentId,
            List<DepartmentPosterRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            organizationMapper.deleteDepartmentPosters(departmentId);
            return;
        }
        Set<Long> keptIds = new HashSet<>();
        for (DepartmentPosterRequest request : requests) {
            if (request.id() != null) {
                keptIds.add(request.id());
            }
        }
        List<Long> removedIds = organizationMapper
                .selectDepartmentPosters(departmentId)
                .stream()
                .map(DepartmentPoster::getId)
                .filter(id -> !keptIds.contains(id))
                .toList();
        if (!removedIds.isEmpty()) {
            organizationMapper.deleteDepartmentPostersByIds(
                    departmentId, removedIds
            );
        }
        for (int index = 0; index < requests.size(); index++) {
            DepartmentPosterRequest request = requests.get(index);
            int sortOrder = request.sortOrder() == null
                    ? index : request.sortOrder();
            if (request.id() != null) {
                organizationMapper.updateDepartmentPosterSortOrder(
                        departmentId, request.id(), sortOrder
                );
                continue;
            }
            DepartmentPoster poster = new DepartmentPoster();
            poster.setDepartmentId(departmentId);
            poster.setUrl(request.url().trim());
            poster.setSortOrder(sortOrder);
            organizationMapper.insertDepartmentPoster(poster);
        }
    }

    private void replaceAchievements(
            Long departmentId,
            List<DepartmentAchievementRequest> requests
    ) {
        organizationMapper.deleteDepartmentAchievements(departmentId);
        if (requests == null) {
            return;
        }
        for (int index = 0; index < requests.size(); index++) {
            DepartmentAchievementRequest request = requests.get(index);
            DepartmentAchievement achievement = new DepartmentAchievement();
            achievement.setDepartmentId(departmentId);
            achievement.setTitle(request.title().trim());
            achievement.setContent(trimToNull(request.content()));
            achievement.setSortOrder(index);
            organizationMapper.insertDepartmentAchievement(achievement);
        }
    }

    private DepartmentVO toVO(Department department, String casId) {
        List<DepartmentPosterVO> posters = toPosterVOs(
                organizationMapper.selectDepartmentPosters(department.getId())
        );
        List<DepartmentAchievementVO> achievements = organizationMapper
                .selectDepartmentAchievements(department.getId())
                .stream()
                .map(DepartmentAchievementVO::from)
                .toList();
        return DepartmentVO.from(
                department,
                posters,
                achievements,
                questionnaireMapper.countQuestions(department.getId()) > 0,
                canManage(casId, department.getId())
        );
    }

    private List<DepartmentPosterVO> toPosterVOs(
            List<DepartmentPoster> posters
    ) {
        return posters.stream()
                .map(poster -> DepartmentPosterVO.from(
                        poster, posterStorage.accessUrl(poster.getUrl())))
                .toList();
    }

    /**
     * 计算展示层的 canManage 标记。
     * 不做缓存：这一次查询走 cas_id 索引且关联表都很小，实时查询可以让角色
     * 授予和撤销立刻反映到前端，也避免缓存条目随用户数无限增长。
     */
    private boolean canManage(String casId, Long departmentId) {
        if (casId == null) {
            return false;
        }
        return authorizationService.canAccessWithPermission(
                casId,
                PermissionCode.RECRUITMENT_MANAGE.code(),
                OrgType.DEPARTMENT,
                departmentId
        );
    }
}
