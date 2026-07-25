package cn.sduonline.join.service;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInQrCodeVO;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentCheckInMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentCheckInService {

    private static final String TOKEN_KEY_PREFIX = "join:check-in:token:";

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentApplicationMapper applicationMapper;
    private final DepartmentCheckInMapper checkInMapper;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final InterviewSseService interviewSseService;

    public ServiceResult<CheckInQrCodeVO> createQrCode(Long departmentId) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        long ttlSeconds = Math.max(
                5, appProperties.getCheckIn().getTokenTtlSeconds()
        );
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + token,
                departmentId.toString(),
                Duration.ofSeconds(ttlSeconds)
        );
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        long refreshSeconds = Math.max(
                1, Math.min(
                        appProperties.getCheckIn().getQrRefreshSeconds(),
                        ttlSeconds - 1
                )
        );
        String content = appProperties.getFrontendUrl()
                + "/check-in?token=" + token;
        return ServiceResult.success(new CheckInQrCodeVO(
                content, expiresAt, refreshSeconds
        ));
    }

    @Transactional
    public ServiceResult<CheckInVO> checkIn(String token, String casId) {
        String departmentValue = redisTemplate.opsForValue().get(
                TOKEN_KEY_PREFIX + token
        );
        if (departmentValue == null) {
            return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
        }
        Long departmentId;
        try {
            departmentId = Long.valueOf(departmentValue);
        } catch (NumberFormatException exception) {
            return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
        }
        DepartmentApplication application =
                applicationMapper.selectByDepartmentAndUser(departmentId, casId);
        if (application == null) {
            return ServiceResult.failure(BizCode.CHECK_IN_NOT_REGISTERED);
        }
        DepartmentCheckIn existing =
                checkInMapper.selectByApplicationId(application.getId());
        if (existing != null) {
            return ServiceResult.success(CheckInVO.from(existing));
        }
        DepartmentCheckIn checkIn = new DepartmentCheckIn();
        checkIn.setDepartmentId(departmentId);
        checkIn.setApplicationId(application.getId());
        checkIn.setCasId(casId);
        checkIn.setCheckedInAt(LocalDateTime.now());
        checkInMapper.initializeSequence(departmentId);
        int queueNumber =
                checkInMapper.selectNextNumberForUpdate(departmentId);
        checkInMapper.incrementNextNumber(departmentId);
        checkIn.setQueueNumber(queueNumber);
        checkIn.setQueueOrder((long) queueNumber);
        checkIn.setPassCount(0);
        try {
            checkInMapper.insert(checkIn);
        } catch (DuplicateKeyException exception) {
            // 同一用户并发扫码时返回已经落库的同一条签到记录。
            return ServiceResult.success(CheckInVO.from(
                    checkInMapper.selectByApplicationId(application.getId())
            ));
        }
        interviewSseService.publishAfterCommit(departmentId);
        return ServiceResult.success(CheckInVO.from(checkIn));
    }

}
