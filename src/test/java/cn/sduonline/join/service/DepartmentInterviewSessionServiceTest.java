package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.InterviewSessionRequest;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.InterviewSessionStatus;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentInterviewSessionServiceTest {

    @Mock AdminOrganizationMapper organizationMapper;
    @Mock DepartmentInterviewSessionMapper sessionMapper;
    private DepartmentInterviewSessionService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentInterviewSessionService(
                organizationMapper, sessionMapper
        );
    }

    @Test
    void createsDraftWithTimeLocationAndLimit() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());

        var result = service.create(12L, request());

        assertTrue(result.isSuccess());
        assertEquals("中心校区 101", result.data().location());
        assertEquals(50, result.data().checkInLimit());
        assertTrue(result.data().qrCheckInEnabled());
        assertEquals(90, result.data().qrCodeTtlSeconds());
        assertEquals(InterviewSessionStatus.DRAFT, result.data().status());
        verify(sessionMapper).insert(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void defaultsQrCodeTtlToEightSeconds() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        InterviewSessionRequest configured = request();
        InterviewSessionRequest withoutTtl = new InterviewSessionRequest(
                configured.startsAt(), configured.endsAt(),
                configured.location(), configured.checkInLimit(),
                configured.qrCheckInEnabled(), null
        );

        var result = service.create(12L, withoutTtl);

        assertTrue(result.isSuccess());
        assertEquals(8, result.data().qrCodeTtlSeconds());
    }

    @Test
    void publishingAssignsPendingCarryoversToThisNextSession() {
        DepartmentInterviewSession published = session();
        when(sessionMapper.publish(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(sessionMapper.selectById(12L, 30L)).thenReturn(published);

        var result = service.publish(12L, 30L);

        assertTrue(result.isSuccess());
        verify(sessionMapper).assignPendingCarryovers(12L, 30L);
    }

    @Test
    void endingSessionCreatesCarryoversForNeverCalledCandidates() {
        DepartmentInterviewSession ended = session();
        ended.setStatus(InterviewSessionStatus.ENDED);
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(session());
        when(sessionMapper.end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(sessionMapper.selectById(12L, 30L)).thenReturn(ended);

        var result = service.end(12L, 30L);

        assertTrue(result.isSuccess());
        var order = inOrder(sessionMapper);
        order.verify(sessionMapper).selectPublishedForUpdate(12L, 30L);
        order.verify(sessionMapper).countActiveInterviews(30L);
        order.verify(sessionMapper).end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        );
        verify(sessionMapper).cancelUnusedCarryovers(30L);
        verify(sessionMapper).createCarryovers(30L);
    }

    @Test
    void refusesToEndWhileAnInterviewIsStillActive() {
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(session());
        when(sessionMapper.countActiveInterviews(30L)).thenReturn(1);

        var result = service.end(12L, 30L);

        assertEquals(
                BizCode.INTERVIEW_SESSION_STATE_INVALID, result.error()
        );
        verify(sessionMapper, never()).end(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void refusesToEndUnlessPublishedSessionCanBeLocked() {
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(null);

        var result = service.end(12L, 30L);

        assertEquals(
                BizCode.INTERVIEW_SESSION_STATE_INVALID, result.error()
        );
        verify(sessionMapper, never()).countActiveInterviews(30L);
        verify(sessionMapper, never()).end(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void autoEndsExpiredSessionsWithNoActiveInterviews() {
        DepartmentInterviewSession expired = session();
        DepartmentInterviewSession ended = session();
        ended.setStatus(InterviewSessionStatus.ENDED);
        when(sessionMapper.selectExpiredPublished(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of(expired));
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(session());
        when(sessionMapper.end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(sessionMapper.selectById(12L, 30L)).thenReturn(ended);

        service.autoEndExpiredSessions();

        verify(sessionMapper).end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void leavesExpiredSessionOpenWhileAnInterviewIsStillActive() {
        DepartmentInterviewSession expired = session();
        when(sessionMapper.selectExpiredPublished(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of(expired));
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(session());
        when(sessionMapper.countActiveInterviews(30L)).thenReturn(1);

        service.autoEndExpiredSessions();

        verify(sessionMapper, never()).end(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static InterviewSessionRequest request() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 8, 1, 9, 0);
        return new InterviewSessionRequest(
                startsAt, startsAt.plusHours(3), " 中心校区 101 ",
                50, true, 90
        );
    }

    private static DepartmentInterviewSession session() {
        DepartmentInterviewSession session =
                new DepartmentInterviewSession();
        session.setId(30L);
        session.setDepartmentId(12L);
        session.setStartsAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        session.setEndsAt(LocalDateTime.of(2026, 8, 1, 12, 0));
        session.setLocation("中心校区 101");
        session.setCheckInLimit(50);
        session.setQrCheckInEnabled(true);
        session.setQrCodeTtlSeconds(90);
        session.setStatus(InterviewSessionStatus.PUBLISHED);
        return session;
    }
}
