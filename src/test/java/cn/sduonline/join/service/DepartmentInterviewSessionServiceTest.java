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
    @Mock org.springframework.transaction.support.TransactionTemplate
            transactionTemplate;
    @Mock org.springframework.transaction.TransactionStatus transactionStatus;
    private DepartmentInterviewSessionService service;

    @BeforeEach
    void setUp() {
        service = new DepartmentInterviewSessionService(
                organizationMapper, sessionMapper, transactionTemplate
        );
    }

    private void executeTransactionsInline() {
        org.mockito.Mockito.doAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?>
                    callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        }).when(transactionTemplate).execute(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void createsDraftWithTimeLocationAndLimit() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());

        var result = service.create(12L, request());

        assertTrue(result.isSuccess());
        assertEquals("中心校区上午场", result.data().name());
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
                configured.name(),
                configured.startsAt(), configured.endsAt(),
                configured.location(), configured.checkInLimit(),
                configured.qrCheckInEnabled(), null
        );

        var result = service.create(12L, withoutTtl);

        assertTrue(result.isSuccess());
        assertEquals(8, result.data().qrCodeTtlSeconds());
    }

    @Test
    void publishingAllowsAnotherSessionToBeOpenAtTheSameTime() {
        DepartmentInterviewSession published = session();
        when(sessionMapper.publish(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(sessionMapper.selectById(12L, 30L)).thenReturn(published);

        var result = service.publish(12L, 30L);

        assertTrue(result.isSuccess());
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
        order.verify(sessionMapper).end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        );
        verify(sessionMapper).cancelUnusedCarryovers(12L, 30L);
        verify(sessionMapper).createCarryovers(30L);
    }

    @Test
    void endingCancelsOutstandingCarryoversBeforeIssuingNewOnes() {
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
        // 顺序不能反：先清掉本部门没用掉的资格，再按本场到场情况重新发放，
        // 否则刚发的那一批会被立刻作废。
        var order = inOrder(sessionMapper);
        order.verify(sessionMapper).cancelUnusedCarryovers(12L, 30L);
        order.verify(sessionMapper).createCarryovers(30L);
    }

    @Test
    void endsTheSessionEvenWhileAnInterviewIsStillActive() {
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

        // 结束场次只关签到，进行中的面试和排队的人都不受影响。
        assertTrue(result.isSuccess());
        verify(sessionMapper).createCarryovers(30L);
    }

    @Test
    void refusesToEndUnlessPublishedSessionCanBeLocked() {
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(null);

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
    void autoEndsExpiredSessionsWithNoActiveInterviews() {
        executeTransactionsInline();
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
    void autoEndsExpiredSessionEvenWhileAnInterviewIsStillActive() {
        executeTransactionsInline();
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

        // 到点只关签到，不等场内面试做完——排队的人继续面完。
        verify(sessionMapper).end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(30L),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void keepsAutoEndingOtherSessionsWhenOneFails() {
        executeTransactionsInline();
        DepartmentInterviewSession failing = session();
        DepartmentInterviewSession healthy = session();
        healthy.setId(31L);
        DepartmentInterviewSession ended = session();
        ended.setId(31L);
        ended.setStatus(InterviewSessionStatus.ENDED);
        when(sessionMapper.selectExpiredPublished(
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(java.util.List.of(failing, healthy));
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenThrow(new IllegalStateException("db is unhappy"));
        when(sessionMapper.selectPublishedForUpdate(12L, 31L))
                .thenReturn(healthy);
        when(sessionMapper.end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(1);
        when(sessionMapper.selectById(12L, 31L)).thenReturn(ended);

        service.autoEndExpiredSessions();

        verify(sessionMapper).end(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(31L),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static InterviewSessionRequest request() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 8, 1, 9, 0);
        return new InterviewSessionRequest(
                " 中心校区上午场 ",
                startsAt, startsAt.plusHours(3), " 中心校区 101 ",
                50, true, 90
        );
    }

    private static DepartmentInterviewSession session() {
        DepartmentInterviewSession session =
                new DepartmentInterviewSession();
        session.setId(30L);
        session.setDepartmentId(12L);
        session.setName("中心校区上午场");
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
