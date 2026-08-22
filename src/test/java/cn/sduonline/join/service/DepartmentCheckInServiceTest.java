package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentCheckInMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    void rejectsUserWhoDidNotApplyToDepartment() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
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
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
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
    void repeatedScanReturnsExistingCheckIn() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
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

        assertTrue(result.isSuccess());
        assertEquals(200L, result.data().id());
        verify(checkInMapper, never()).insert(any());
    }

    @Test
    void rejectsCheckInWhenSessionLimitIsReached() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12:30");
        DepartmentInterviewSession session = openSession();
        session.setCheckInLimit(1);
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
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
        Department department = new Department();
        department.setQrCheckInEnabled(true);
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(openSession());
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
    void allowsDirectCheckInWhenDepartmentDoesNotRequireQrCode() {
        Department department = new Department();
        department.setId(12L);
        department.setQrCheckInEnabled(false);
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(sessionMapper.selectPublished(12L)).thenReturn(openSession());
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
                .thenReturn(openSession());
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.selectNextNumberForUpdate(30L)).thenReturn(1);

        var result = service.checkIn(12L, null, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(12L, result.data().departmentId());
        verify(sessionMapper, never()).selectPendingCarryoverForUpdate(
                any(), any(), any()
        );
    }

    @Test
    void requiresQrTokenWhenDepartmentEnablesQrCheckIn() {
        Department department = new Department();
        department.setQrCheckInEnabled(true);
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);

        var result = service.checkIn(12L, null, "20240001");

        assertEquals(BizCode.CHECK_IN_TOKEN_INVALID, result.error());
        verify(applicationMapper, never())
                .selectByDepartmentAndUser(any(), any());
    }

    @Test
    void checkingInAgainRejoinsQueueAtTheEnd() {
        Department department = new Department();
        department.setQrCheckInEnabled(false);
        when(organizationMapper.selectDepartmentById(12L)).thenReturn(department);
        when(sessionMapper.selectPublished(12L)).thenReturn(openSession());
        when(sessionMapper.selectPublishedForUpdate(12L, 30L))
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

        var result = service.checkIn(12L, null, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(9, result.data().queueNumber());
        verify(checkInMapper).reactivateAfterCheckIn(existing);
    }

    private static DepartmentInterviewSession openSession() {
        DepartmentInterviewSession session = new DepartmentInterviewSession();
        session.setId(30L);
        session.setDepartmentId(12L);
        session.setCheckInLimit(50);
        return session;
    }
}
