package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DashboardApplicationStatisticsVO;
import cn.sduonline.join.data.dto.DashboardCollegeDistributionVO;
import cn.sduonline.join.data.dto.DashboardDepartmentApplicationVO;
import cn.sduonline.join.data.dto.DashboardHighlightedSessionVO;
import cn.sduonline.join.data.dto.DashboardInterviewAggregateRow;
import cn.sduonline.join.data.dto.DashboardInterviewStatisticsVO;
import cn.sduonline.join.data.dto.DashboardOverviewVO;
import cn.sduonline.join.data.dto.DashboardScopeDepartmentRow;
import cn.sduonline.join.data.dto.DashboardScopeVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.DashboardStatisticsMapper;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardStatisticsService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final DashboardStatisticsMapper statisticsMapper;
    private final AuthorizationService authorizationService;

    @Transactional(readOnly = true)
    public ServiceResult<DashboardOverviewVO> getOverview(
            String casId,
            OrgType scopeType,
            Long scopeId
    ) {
        if (scopeType == null || scopeId == null || scopeId <= 0) {
            return ServiceResult.failure(BizCode.PARAM_INVALID);
        }

        List<String> roles = authorizationService.findRoles(casId);
        boolean systemAdmin = roles.contains("SYSTEM_ADMIN");
        if (!systemAdmin && !authorizationService.canAccessCompleteScopeWithPermission(
                casId, PermissionCode.STATISTICS_READ.code(), scopeType, scopeId
        )) {
            // 对未授权用户不区分“不存在”和“越权”，避免泄露组织信息。
            return ServiceResult.failure(BizCode.NO_PERMISSION);
        }

        List<DashboardScopeDepartmentRow> scopeRows = resolveScope(
                scopeType, scopeId
        );
        if (scopeRows.isEmpty()) {
            return ServiceResult.failure(BizCode.ORG_SCOPE_NOT_FOUND);
        }

        List<Long> departmentIds = scopeRows.stream()
                .map(DashboardScopeDepartmentRow::departmentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        List<DashboardDepartmentApplicationVO> byDepartment;
        List<DashboardCollegeDistributionVO> byCollege;
        if (departmentIds.isEmpty()) {
            byDepartment = List.of();
            byCollege = List.of();
        } else {
            byDepartment = statisticsMapper
                    .selectApplicationCountsByDepartment(departmentIds);
            byCollege = statisticsMapper
                    .selectApplicationCountsByCollege(departmentIds);
        }

        long total = byDepartment.stream()
                .mapToLong(DashboardDepartmentApplicationVO::applicationCount)
                .sum();
        int departmentsWithApplications = Math.toIntExact(byDepartment.stream()
                .filter(item -> item.applicationCount() > 0)
                .count());
        double average = departmentIds.isEmpty()
                ? 0.0 : (double) total / departmentIds.size();

        DashboardScopeDepartmentRow first = scopeRows.getFirst();
        DashboardScopeVO scope = new DashboardScopeVO(
                scopeType, first.scopeId(), first.scopeName(), departmentIds.size()
        );
        DashboardApplicationStatisticsVO applications =
                new DashboardApplicationStatisticsVO(
                        total,
                        departmentsWithApplications,
                        average,
                        List.copyOf(byDepartment),
                        List.copyOf(byCollege)
                );
        DashboardInterviewStatisticsVO interviews = scopeType == OrgType.DEPARTMENT
                ? buildInterviewStatistics(scopeId) : null;

        return ServiceResult.success(new DashboardOverviewVO(
                scope,
                applications,
                interviews,
                OffsetDateTime.now(DISPLAY_ZONE)
        ));
    }

    private List<DashboardScopeDepartmentRow> resolveScope(
            OrgType scopeType,
            Long scopeId
    ) {
        return switch (scopeType) {
            case BOARD -> statisticsMapper.selectBoardScope(scopeId);
            case WORKSTATION -> statisticsMapper.selectWorkstationScope(scopeId);
            case DEPARTMENT -> statisticsMapper.selectDepartmentScope(scopeId);
        };
    }

    private DashboardInterviewStatisticsVO buildInterviewStatistics(Long departmentId) {
        DashboardInterviewAggregateRow aggregate =
                statisticsMapper.selectInterviewAggregate(departmentId);
        DepartmentInterviewSession session =
                statisticsMapper.selectHighlightedSession(departmentId);
        DashboardHighlightedSessionVO highlighted = session == null
                ? null : new DashboardHighlightedSessionVO(
                        session.getId(),
                        session.getName(),
                        session.getLocation(),
                        toOffsetDateTime(session.getStartsAt()),
                        toOffsetDateTime(session.getEndsAt()),
                        session.getStatus()
                );
        return new DashboardInterviewStatisticsVO(
                aggregate == null || aggregate.publishedSessionCount() == null
                        ? 0 : aggregate.publishedSessionCount(),
                aggregate == null || aggregate.operationalSessionCount() == null
                        ? 0 : aggregate.operationalSessionCount(),
                highlighted,
                aggregate == null || aggregate.waitingCount() == null
                        ? 0L : aggregate.waitingCount()
        );
    }

    private static OffsetDateTime toOffsetDateTime(java.time.LocalDateTime value) {
        return value == null
                ? null : value.atZone(DISPLAY_ZONE).toOffsetDateTime();
    }
}
