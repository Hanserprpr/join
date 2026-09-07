package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.InterviewSessionStatus;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentCheckInMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class DepartmentCheckInServiceTest {

    @Mock AdminOrganizationMapper organizationMapper;
    @Mock DepartmentApplicationMapper applicationMapper;
    @Mock DepartmentCheckInMapper checkInMapper;
    @Mock DepartmentInterviewSessionMapper sessionMapper;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock InterviewSseService interviewSseService;
    private DepartmentCheckInService service;

    @BeforeEach
    void setUp() {
        // 非二维码签到路径不碰 Redis，宽松处理避免严格模式判为多余桩
        lenient().when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);
        service = new DepartmentCheckInService(
                organizationMapper, applicationMapper, checkInMapper,
                sessionMapper,
                redisTemplate, new AppProperties(), interviewSseService
        );
    }

    @Test
    void rejectsExpiredQrCode() {
        when(valueOperations.get("join:check-in:token:expired"))
                .thenReturn(null);

        var result = service.checkIn("expired", "20240001");

        assertEquals(BizCode.CHECK_IN_TOKEN_INVALID, result.error());
        verify(applicationMapper, never())
                .selectByDepartmentAndUser(any(), any());
    }

    @Test
    void createsQrCodeUsingSessionTtl() {
        Department department = new Department();
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(department);
        DepartmentInterviewSession session = openSession();
        session.setQrCheckInEnabled(true);
        session.setQrCodeTtlSeconds(90);
        when(sessionMapper.selectPublishedById(12L, 30L)).thenReturn(session);

        var result = service.createQrCode(12L, 30L);

        assertTrue(result.isSuccess());
        assertTrue(result.data().content().startsWith(
                "https://i.sdu.edu.cn/recruit/api/wechat/check-in/entry?token="));
        assertEquals(89, result.data().refreshAfterSeconds());
        verify(valueOperations).set(
                any(), eq("12:30"), eq(Duration.ofSeconds(90))
        );
    }

    @Test
    void stopsCreatingQrCodesAfterSessionEndTime() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentInterviewSession session = openSession();
        session.setQrCheckInEnabled(true);
        session.setEndsAt(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectPublishedById(12L, 30L)).thenReturn(session);

        var result = service.createQrCode(12L, 30L);

        assertEquals(BizCode.INTERVIEW_SESSION_NOT_OPEN, result.error());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void capturedGrantRemainsUsableAfterShortLivedQrTokenExpires() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");

        var captured = service.captureQrGrant("valid");

        assertTrue(captured.isSuccess());
        assertEquals(12L, captured.data().departmentId());
        assertEquals(30L, captured.data().sessionId());

        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(3);

        var result = service.checkInCaptured(
                captured.data(), "20240001");

        assertTrue(result.isSuccess());
        assertEquals(3, result.data().queueNumber());
        verify(valueOperations, times(1))
                .get("join:check-in:token:valid");
    }

    @Test
    void rejectsUserWhoDidNotApplyToDepartment() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(null);

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.CHECK_IN_NOT_REGISTERED, result.error());
        verify(checkInMapper, never()).insert(any());
    }

    @Test
    void createsOneCheckInForRegisteredUser() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(null);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(7);
        org.mockito.Mockito.doAnswer(invocation -> {
            DepartmentCheckIn checkIn = invocation.getArgument(0);
            checkIn.setId(200L);
            return 1;
        }).when(checkInMapper).insert(any());

        var result = service.checkIn("valid", "20240001");

        assertTrue(result.isSuccess());
        assertEquals(200L, result.data().id());
        assertEquals(12L, result.data().departmentId());
        assertEquals(30L, result.data().sessionId());
        assertEquals(100L, result.data().applicationId());
        assertEquals(7, result.data().queueNumber());
        verify(checkInMapper).initializeSequence(30L);
        verify(checkInMapper).incrementNextNumber(30L);
    }

    @Test
    void rejectsCheckInWhenAlreadyQueuedInAnotherPublishedSession() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.countOtherSessionCheckIns(12L, 100L, 30L))
                .thenReturn(1);

        var result = service.checkIn("valid", "20240001");

        assertEquals(
                BizCode.CHECK_IN_OTHER_SESSION_EXISTS, result.error()
        );
        verify(checkInMapper, never()).insert(any());
        verify(checkInMapper, never()).initializeSequence(any());
    }

    @Test
    void rejectsNewCheckInAfterSessionEndTime() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        DepartmentInterviewSession session = openSession();
        session.setEndsAt(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.INTERVIEW_SESSION_NOT_OPEN, result.error());
        verify(checkInMapper, never()).insert(any());
        verify(checkInMapper, never()).initializeSequence(any());
    }

    @Test
    void repeatedScanReturnsAlreadyCheckedInError() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = new DepartmentCheckIn();
        existing.setId(200L);
        existing.setDepartmentId(12L);
        existing.setSessionId(30L);
        existing.setApplicationId(100L);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(existing);

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.CHECK_IN_ALREADY_EXISTS, result.error());
        verify(checkInMapper, never()).insert(any());
    }

    @Test
    void repeatedScanReturnsAlreadyCheckedInErrorAfterSessionEndTime() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        DepartmentInterviewSession session = openSession();
        session.setEndsAt(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = new DepartmentCheckIn();
        existing.setId(200L);
        existing.setDepartmentId(12L);
        existing.setSessionId(30L);
        existing.setApplicationId(100L);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(existing);

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.CHECK_IN_ALREADY_EXISTS, result.error());
        verify(checkInMapper, never()).insert(any());
    }

    @Test
    void concurrentRepeatedScanReturnsAlreadyCheckedInError() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = new DepartmentCheckIn();
        existing.setId(200L);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(null, existing);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(7);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("duplicate"))
                .when(checkInMapper).insert(any());

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.CHECK_IN_ALREADY_EXISTS, result.error());
    }

    @Test
    void rejectsCheckInWhenSessionLimitIsReached() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        DepartmentInterviewSession session = openSession();
        session.setCheckInLimit(1);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(sessionMapper.countCheckIns(30L)).thenReturn(1);

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.INTERVIEW_SESSION_FULL, result.error());
        verify(checkInMapper, never()).insert(any());
    }

    @Test
    void qrCheckInMarksCarryoverCandidateAsPriority() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        DepartmentInterviewSession session = openSession();
        session.setQrCheckInEnabled(true);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(sessionMapper.selectPendingCarryoverForUpdate(
                12L, 100L, 30L
        )).thenReturn(900L);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(1);

        var result = service.checkIn("valid", "20240001");

        assertTrue(result.isSuccess());
        assertTrue(result.data().priority());
        verify(sessionMapper).useCarryover(900L, 30L);
    }

    @Test
    void allowsDirectCheckInWhenSessionDoesNotRequireQrCode() {
        Department department = new Department();
        department.setId(12L);
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(1);

        var result = service.checkIn(12L, 30L, null, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(12L, result.data().departmentId());
        verify(sessionMapper, never()).selectPendingCarryoverForUpdate(
                any(), any(), any()
        );
    }

    @Test
    void requiresQrTokenWhenSessionEnablesQrCheckIn() {
        Department department = new Department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentInterviewSession session = openSession();
        session.setQrCheckInEnabled(true);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L)).thenReturn(session);

        var result = service.checkIn(12L, 30L, null, "20240001");

        assertEquals(BizCode.CHECK_IN_TOKEN_INVALID, result.error());
        verify(applicationMapper, never())
                .selectByDepartmentAndUser(any(), any());
    }

    @Test
    void checkingInAgainRejoinsQueueAtTheEnd() {
        Department department = new Department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = new DepartmentCheckIn();
        existing.setId(200L);
        existing.setDepartmentId(12L);
        existing.setSessionId(30L);
        existing.setApplicationId(100L);
        existing.setRequiresRecheckIn(true);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(existing);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(9);

        var result = service.checkIn(12L, 30L, null, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(9, result.data().queueNumber());
        verify(checkInMapper).reactivateAfterCheckIn(existing);
    }

    @Test
    void allowsRecheckInAfterSessionEndTime() {
        Department department = new Department();
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        DepartmentInterviewSession session = openSession();
        session.setEndsAt(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = new DepartmentCheckIn();
        existing.setId(200L);
        existing.setDepartmentId(12L);
        existing.setSessionId(30L);
        existing.setApplicationId(100L);
        existing.setRequiresRecheckIn(true);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(existing);

        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(9);

        var result = service.checkIn(12L, 30L, null, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(9, result.data().queueNumber());
        verify(checkInMapper).reactivateAfterCheckIn(existing);
        verify(checkInMapper, never()).insert(any());
    }

    @ParameterizedTest
    @CsvSource({
            "ENDED, true, false", "ENDED, false, false",
            "ENDED, true, true", "ENDED, false, true",
            "PUBLISHED, true, false", "PUBLISHED, false, false",
            "PUBLISHED, true, true", "PUBLISHED, false, true"
    })
    void restoresPassedCandidateAfterCheckInCloses(
            InterviewSessionStatus status, boolean requiresQr, boolean captured
    ) {
        DepartmentInterviewSession session = closedSession(status);
        session.setQrCheckInEnabled(requiresQr);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = currentCheckIn();
        existing.setRequiresRecheckIn(true);
        existing.setPassCount(2);
        existing.setPriority(false);
        when(checkInMapper.selectBySessionAndApplication(30L, 100L))
                .thenReturn(existing);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(9);
        if (!captured) {
            when(organizationMapper.selectDepartmentById(12L))
                    .thenReturn(new Department());
        }

        var result = captured
                ? service.checkInCaptured(new CheckInQrGrant(12L, 30L), "20240001")
                : service.checkIn(12L, 30L, null, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(200L, result.data().id());
        assertEquals(30L, result.data().sessionId());
        assertEquals(9, result.data().queueNumber());
        assertEquals(9L, existing.getQueueOrder());
        assertEquals(2, existing.getPassCount());
        assertEquals(false, existing.getPriority());
        assertEquals(false, existing.getRequiresRecheckIn());
        verify(checkInMapper).reactivateAfterCheckIn(existing);
        verify(checkInMapper, never()).insert(any());
        verify(sessionMapper, never()).countCheckIns(any());
        verify(sessionMapper, never()).selectPendingCarryoverForUpdate(any(), any(), any());
        verify(interviewSseService).publishQueueAfterCommit(12L, 30L);
        verifyNoInteractions(redisTemplate);

        // 恢复后重复提交不能再次取号。
        var repeated = captured
                ? service.checkInCaptured(new CheckInQrGrant(12L, 30L), "20240001")
                : service.checkIn(12L, 30L, null, "20240001");
        assertEquals(BizCode.CHECK_IN_ALREADY_EXISTS, repeated.error());
        verify(checkInMapper, times(1)).incrementNextNumber(30L);
        verify(checkInMapper, times(1)).reactivateAfterCheckIn(existing);
    }

    @ParameterizedTest
    @CsvSource({"ENDED, true", "ENDED, false", "PUBLISHED, true", "PUBLISHED, false"})
    void closedSessionStillRejectsNewDirectCheckIns(
            InterviewSessionStatus status, boolean requiresQr
    ) {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentInterviewSession session = closedSession(status);
        session.setQrCheckInEnabled(requiresQr);
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(session);
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);

        var result = service.checkIn(12L, 30L, null, "20240001");

        assertEquals(BizCode.INTERVIEW_SESSION_NOT_OPEN, result.error());
        verify(checkInMapper, never()).insert(any());
        verify(checkInMapper, never()).reactivateAfterCheckIn(any());
        verify(checkInMapper, never()).initializeSequence(any());
        verifyNoInteractions(interviewSseService);
    }

    @Test
    void recoveryCannotRequeueSomeoneAlreadyInterviewed() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(sessionMapper.selectCheckInSessionForUpdate(12L, 30L))
                .thenReturn(closedSession(InterviewSessionStatus.ENDED));
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.countInterviewsByApplication(100L)).thenReturn(1);

        var result = service.checkIn(12L, 30L, null, "20240001");

        assertEquals(BizCode.STATE_NOT_ALLOWED, result.error());
        verify(checkInMapper, never()).reactivateAfterCheckIn(any());
        verify(checkInMapper, never()).initializeSequence(any());
    }

    private static DepartmentInterviewSession closedSession(InterviewSessionStatus status) {
        DepartmentInterviewSession session = openSession();
        session.setStatus(status);
        // ENDED 覆盖提前手动结束；PUBLISHED 覆盖到点但定时任务尚未执行。
        session.setEndsAt(status == InterviewSessionStatus.ENDED
                ? LocalDateTime.now().plusHours(1)
                : LocalDateTime.now().minusMinutes(1));
        return session;
    }

    @Test
    void cancelsOwnWaitingCheckInWithoutRestoringPriority() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentCheckIn checkIn = currentCheckIn();
        checkIn.setPriority(true);
        when(checkInMapper.selectCurrentByDepartmentAndUserForUpdate(
                12L, 30L, "20240001"
        )).thenReturn(checkIn);
        when(checkInMapper.countInterviewsByCheckIn(200L)).thenReturn(0);
        when(checkInMapper.deleteOwnedCheckIn(
                200L, 12L, "20240001"
        )).thenReturn(1);

        var result = service.cancelCheckIn(12L, 30L, "20240001");

        assertTrue(result.isSuccess());
        verifyNoInteractions(sessionMapper);
        verify(checkInMapper).deleteOwnedCheckIn(
                200L, 12L, "20240001"
        );
        verify(interviewSseService).publishQueueAfterCommit(12L, 30L);
    }

    @Test
    void rejectsCheckInCancellationAfterInterviewStarts() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentCheckIn checkIn = currentCheckIn();
        when(checkInMapper.selectCurrentByDepartmentAndUserForUpdate(
                12L, 30L, "20240001"
        )).thenReturn(checkIn);
        when(checkInMapper.countInterviewsByCheckIn(200L)).thenReturn(1);

        var result = service.cancelCheckIn(12L, 30L, "20240001");

        assertEquals(BizCode.CHECK_IN_CANNOT_CANCEL, result.error());
        verify(checkInMapper, never()).deleteOwnedCheckIn(
                any(), any(), any()
        );
        verify(interviewSseService, never())
                .publishQueueAfterCommit(any(), any());
    }

    @Test
    void rejectsCancellationWhenCurrentCheckInDoesNotExist() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(checkInMapper.selectCurrentByDepartmentAndUserForUpdate(
                12L, 30L, "20240001"
        )).thenReturn(null);

        var result = service.cancelCheckIn(12L, 30L, "20240001");

        assertEquals(BizCode.CHECK_IN_NOT_FOUND, result.error());
        verify(checkInMapper, never()).deleteOwnedCheckIn(
                any(), any(), any()
        );
    }

    private static DepartmentCheckIn currentCheckIn() {
        DepartmentCheckIn checkIn = new DepartmentCheckIn();
        checkIn.setId(200L);
        checkIn.setDepartmentId(12L);
        checkIn.setSessionId(30L);
        checkIn.setApplicationId(100L);
        checkIn.setCasId("20240001");
        return checkIn;
    }

    private static DepartmentInterviewSession openSession() {
        DepartmentInterviewSession session = new DepartmentInterviewSession();
        session.setId(30L);
        session.setDepartmentId(12L);
        session.setCheckInLimit(50);
        session.setStatus(InterviewSessionStatus.PUBLISHED);
        return session;
    }
}
