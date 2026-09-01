package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.DashboardCollegeDistributionVO;
import cn.sduonline.join.data.dto.DashboardDepartmentApplicationVO;
import cn.sduonline.join.data.dto.DashboardInterviewAggregateRow;
import cn.sduonline.join.data.dto.DashboardOverviewVO;
import cn.sduonline.join.data.dto.DashboardScopeDepartmentRow;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.InterviewSessionStatus;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.DashboardStatisticsMapper;
import cn.sduonline.join.security.scope.OrgType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardStatisticsServiceTest {

    @Mock
    private DashboardStatisticsMapper statisticsMapper;
    @Mock
    private AuthorizationService authorizationService;

    private DashboardStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new DashboardStatisticsService(
                statisticsMapper, authorizationService
        );
    }

    @Test
    void workstationOverviewAggregatesAllDepartmentsWithoutInterviewQueries() {
        when(authorizationService.findRoles("operator")).thenReturn(List.of(
                "WORKSTATION_ADMIN"
        ));
        when(authorizationService.canAccessCompleteScopeWithPermission(
                "operator", "statistics:read", OrgType.WORKSTATION, 21L
        )).thenReturn(true);
        when(statisticsMapper.selectWorkstationScope(21L)).thenReturn(List.of(
                new DashboardScopeDepartmentRow(
                        21L, "技术工作站", 101L, "网站开发部",
                        21L, "技术工作站"),
                new DashboardScopeDepartmentRow(
                        21L, "技术工作站", 102L, "移动开发部",
                        21L, "技术工作站")
        ));
        List<Long> departmentIds = List.of(101L, 102L);
        when(statisticsMapper.selectApplicationCountsByDepartment(departmentIds))
                .thenReturn(List.of(
                        new DashboardDepartmentApplicationVO(
                                101L, "网站开发部", 21L, "技术工作站", 3L),
                        new DashboardDepartmentApplicationVO(
                                102L, "移动开发部", 21L, "技术工作站", 0L)
                ));
        when(statisticsMapper.selectApplicationCountsByCollege(departmentIds))
                .thenReturn(List.of(
                        new DashboardCollegeDistributionVO("软件学院", 2L),
                        new DashboardCollegeDistributionVO("未知学院", 1L)
                ));

        ServiceResult<DashboardOverviewVO> result = service.getOverview(
                "operator", OrgType.WORKSTATION, 21L
        );

        assertTrue(result.isSuccess());
        assertEquals(2, result.data().scope().departmentCount());
        assertEquals(3L, result.data().applications().total());
        assertEquals(1, result.data().applications().departmentsWithApplications());
        assertEquals(1.5, result.data().applications().averagePerDepartment());
        assertEquals(0L, result.data().applications().byDepartment().get(1)
                .applicationCount());
        assertNull(result.data().interviews());
        verify(statisticsMapper, never()).selectInterviewAggregate(21L);
        verify(statisticsMapper, never()).selectHighlightedSession(21L);
    }

    @Test
    void departmentOverviewIncludesStableInterviewSummary() {
        when(authorizationService.findRoles("admin"))
                .thenReturn(List.of("DEPARTMENT_ADMIN"));
        when(authorizationService.canAccessCompleteScopeWithPermission(
                "admin", "statistics:read", OrgType.DEPARTMENT, 101L
        )).thenReturn(true);
        when(statisticsMapper.selectDepartmentScope(101L)).thenReturn(List.of(
                new DashboardScopeDepartmentRow(
                        101L, "网站开发部", 101L, "网站开发部",
                        21L, "技术工作站")
        ));
        when(statisticsMapper.selectApplicationCountsByDepartment(List.of(101L)))
                .thenReturn(List.of(new DashboardDepartmentApplicationVO(
                        101L, "网站开发部", 21L, "技术工作站", 4L
                )));
        when(statisticsMapper.selectApplicationCountsByCollege(List.of(101L)))
                .thenReturn(List.of(new DashboardCollegeDistributionVO(
                        "软件学院", 4L
                )));
        when(statisticsMapper.selectInterviewAggregate(101L))
                .thenReturn(new DashboardInterviewAggregateRow(2, 3, 17L));
        DepartmentInterviewSession session = new DepartmentInterviewSession();
        session.setId(501L);
        session.setDepartmentId(101L);
        session.setName("第一轮面试");
        session.setLocation("软件园校区 302");
        session.setStartsAt(LocalDateTime.of(2026, 9, 1, 14, 0));
        session.setEndsAt(LocalDateTime.of(2026, 9, 1, 18, 0));
        session.setStatus(InterviewSessionStatus.PUBLISHED);
        when(statisticsMapper.selectHighlightedSession(101L)).thenReturn(session);

        ServiceResult<DashboardOverviewVO> result = service.getOverview(
                "admin", OrgType.DEPARTMENT, 101L
        );

        assertTrue(result.isSuccess());
        assertEquals(2, result.data().interviews().publishedSessionCount());
        assertEquals(3, result.data().interviews().operationalSessionCount());
        assertEquals(17L, result.data().interviews().waitingCount());
        assertEquals(501L, result.data().interviews().highlightedSession().sessionId());
        assertEquals("PUBLISHED", result.data().interviews()
                .highlightedSession().status().name());
    }

    @Test
    void unauthorizedScopeDoesNotRevealWhetherOrganizationExists() {
        when(authorizationService.findRoles("admin"))
                .thenReturn(List.of("DEPARTMENT_ADMIN"));
        when(authorizationService.canAccessCompleteScopeWithPermission(
                "admin", "statistics:read", OrgType.DEPARTMENT, 999L
        )).thenReturn(false);

        ServiceResult<DashboardOverviewVO> result = service.getOverview(
                "admin", OrgType.DEPARTMENT, 999L
        );

        assertEquals(BizCode.NO_PERMISSION, result.error());
        verify(statisticsMapper, never()).selectDepartmentScope(999L);
    }

    @Test
    void systemAdminReceivesExplicitNotFoundForDisabledOrMissingScope() {
        when(authorizationService.findRoles("root"))
                .thenReturn(List.of("SYSTEM_ADMIN"));
        when(statisticsMapper.selectBoardScope(999L)).thenReturn(List.of());

        ServiceResult<DashboardOverviewVO> result = service.getOverview(
                "root", OrgType.BOARD, 999L
        );

        assertEquals(BizCode.ORG_SCOPE_NOT_FOUND, result.error());
    }
}
