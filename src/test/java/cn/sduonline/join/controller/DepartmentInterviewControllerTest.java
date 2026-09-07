package cn.sduonline.join.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import cn.sduonline.join.data.dto.InterviewSessionVO;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import cn.sduonline.join.service.AuthorizationService;
import cn.sduonline.join.service.DepartmentInterviewService;
import cn.sduonline.join.service.DepartmentInterviewSessionService;
import cn.sduonline.join.service.InterviewSseService;
import cn.sduonline.join.service.ServiceResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DepartmentInterviewControllerTest {

    private DepartmentInterviewSessionService sessionService;
    private AuthorizationService authorizationService;
    private DepartmentInterviewController controller;

    @BeforeEach
    void setUp() {
        sessionService = mock(DepartmentInterviewSessionService.class);
        authorizationService = mock(AuthorizationService.class);
        controller = new DepartmentInterviewController(
                mock(DepartmentInterviewService.class),
                sessionService,
                mock(InterviewSseService.class),
                authorizationService
        );
    }

    @Test
    void managerStillReceivesAllSessions() {
        List<InterviewSessionVO> sessions = List.of();
        when(authorizationService.canAccessWithPermission(
                "admin", PermissionCode.INTERVIEW_MANAGE.code(),
                OrgType.DEPARTMENT, 32L
        )).thenReturn(true);
        when(sessionService.findAll(32L))
                .thenReturn(ServiceResult.success(sessions));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("admin");

            var result = controller.findSessions(32L);

            assertThat(result.getData()).isSameAs(sessions);
        }
        verify(sessionService).findAll(32L);
        verify(sessionService, never()).findPublished(32L);
    }

    @Test
    void regularUserReceivesOnlyPublishedSessions() {
        List<InterviewSessionVO> sessions = List.of();
        when(authorizationService.canAccessWithPermission(
                "student", PermissionCode.INTERVIEW_MANAGE.code(),
                OrgType.DEPARTMENT, 32L
        )).thenReturn(false);
        when(sessionService.findPublished(32L))
                .thenReturn(ServiceResult.success(sessions));

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn("student");

            var result = controller.findSessions(32L);

            assertThat(result.getData()).isSameAs(sessions);
        }
        verify(sessionService).findPublished(32L);
        verify(sessionService, never()).findAll(32L);
    }
}
