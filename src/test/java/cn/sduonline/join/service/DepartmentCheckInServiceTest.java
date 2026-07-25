package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentCheckInMapper;
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
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock InterviewSseService interviewSseService;
    private DepartmentCheckInService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new DepartmentCheckInService(
                organizationMapper, applicationMapper, checkInMapper,
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
                .thenReturn("12");
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(null);

        var result = service.checkIn("valid", "20240001");

        assertEquals(BizCode.CHECK_IN_NOT_REGISTERED, result.error());
        verify(checkInMapper, never()).insert(any());
    }

    @Test
    void createsOneCheckInForRegisteredUser() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12");
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        when(checkInMapper.selectByApplicationId(100L)).thenReturn(null);
        when(checkInMapper.selectNextNumberForUpdate(12L)).thenReturn(7);
        org.mockito.Mockito.doAnswer(invocation -> {
            DepartmentCheckIn checkIn = invocation.getArgument(0);
            checkIn.setId(200L);
            return 1;
        }).when(checkInMapper).insert(any());

        var result = service.checkIn("valid", "20240001");

        assertTrue(result.isSuccess());
        assertEquals(200L, result.data().id());
        assertEquals(12L, result.data().departmentId());
        assertEquals(100L, result.data().applicationId());
        assertEquals(7, result.data().queueNumber());
        verify(checkInMapper).initializeSequence(12L);
        verify(checkInMapper).incrementNextNumber(12L);
    }

    @Test
    void repeatedScanReturnsExistingCheckIn() {
        when(valueOperations.get("join:check-in:token:valid"))
                .thenReturn("12");
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        when(applicationMapper.selectByDepartmentAndUser(12L, "20240001"))
                .thenReturn(application);
        DepartmentCheckIn existing = new DepartmentCheckIn();
        existing.setId(200L);
        existing.setDepartmentId(12L);
        existing.setApplicationId(100L);
        when(checkInMapper.selectByApplicationId(100L)).thenReturn(existing);

        var result = service.checkIn("valid", "20240001");

        assertTrue(result.isSuccess());
        assertEquals(200L, result.data().id());
        verify(checkInMapper, never()).insert(any());
    }
}
