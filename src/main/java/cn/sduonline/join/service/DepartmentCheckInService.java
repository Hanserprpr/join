package cn.sduonline.join.service;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInQrCodeVO;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentApplicationMapper;
import cn.sduonline.join.mapper.DepartmentCheckInMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
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
    private final DepartmentInterviewSessionMapper sessionMapper;
    private final StringRedisTemplate redisTemplate;
    private final AppProperties appProperties;
    private final InterviewSseService interviewSseService;

    public ServiceResult<CheckInQrCodeVO> createQrCode(Long departmentId) {
        Department department = organizationMapper.selectDepartmentById(departmentId);
        if (department == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(department.getQrCheckInEnabled())) {
            return ServiceResult.failure(BizCode.STATE_NOT_ALLOWED);
        }
        DepartmentInterviewSession session =
                sessionMapper.selectPublished(departmentId);
        if (session == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_OPEN);
        }
        long ttlSeconds = Math.max(
                5, appProperties.getCheckIn().getTokenTtlSeconds()
        );
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                TOKEN_KEY_PREFIX + token,
                departmentId + ":" + session.getId(),
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
    public ServiceResult<CheckInVO> checkIn(
            Long requestedDepartmentId, String token, String casId
    ) {
        Long departmentId;
        Long sessionId;
        Department department;
        boolean priorityEligible = false;
        if (token != null && !token.isBlank()) {
            String departmentValue = redisTemplate.opsForValue().get(
                    TOKEN_KEY_PREFIX + token
            );
            if (departmentValue == null) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            try {
                String[] tokenParts = departmentValue.split(":", 2);
                departmentId = Long.valueOf(tokenParts[0]);
                sessionId = Long.valueOf(tokenParts[1]);
            } catch (RuntimeException exception) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            if (requestedDepartmentId != null
                    && !requestedDepartmentId.equals(departmentId)) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            department = organizationMapper.selectDepartmentById(departmentId);
            priorityEligible = department != null
                    && Boolean.TRUE.equals(department.getQrCheckInEnabled());
            // 令牌本身已绑定部门和场次，因此切换开关期间已展示的二维码
            // 仍可完成签到；仅开启二维码模式时才享有跨场次优先资格。
        } else {
            if (requestedDepartmentId == null) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            departmentId = requestedDepartmentId;
            department = organizationMapper.selectDepartmentById(departmentId);
            if (department == null) {
                return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
            }
            if (Boolean.TRUE.equals(department.getQrCheckInEnabled())) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            DepartmentInterviewSession published = sessionMapper.selectPublished(departmentId);
            if (published == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_OPEN);
            }
            sessionId = published.getId();
        }
        DepartmentInterviewSession session =
                sessionMapper.selectPublishedForUpdate(
                        departmentId, sessionId
                );
        if (session == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_OPEN);
        }
        DepartmentApplication application =
                applicationMapper.selectByDepartmentAndUser(departmentId, casId);
        if (application == null) {
            return ServiceResult.failure(BizCode.CHECK_IN_NOT_REGISTERED);
        }
        if (checkInMapper.countInterviewsByApplication(application.getId())
                > 0) {
            return ServiceResult.failure(BizCode.STATE_NOT_ALLOWED);
        }
        DepartmentCheckIn existing =
                checkInMapper.selectBySessionAndApplication(
                        sessionId, application.getId()
                );
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getRequiresRecheckIn())) {
                checkInMapper.initializeSequence(sessionId);
                Integer queueNumber =
                        checkInMapper.selectNextNumberForUpdate(sessionId);
                checkInMapper.incrementNextNumber(sessionId);
                existing.setCheckedInAt(LocalDateTime.now());
                existing.setQueueNumber(queueNumber);
                existing.setQueueOrder((long) queueNumber);
                existing.setRequiresRecheckIn(false);
                checkInMapper.reactivateAfterCheckIn(existing);
                interviewSseService.publishQueueAfterCommit(departmentId);
            }
            return ServiceResult.success(CheckInVO.from(existing));
        }
        if (sessionMapper.countCheckIns(sessionId)
                >= session.getCheckInLimit()) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_FULL);
        }
        Long carryoverId = priorityEligible
                ? sessionMapper.selectPendingCarryoverForUpdate(
                        departmentId, application.getId(), sessionId
                )
                : null;
        DepartmentCheckIn checkIn = new DepartmentCheckIn();
        checkIn.setDepartmentId(departmentId);
        checkIn.setSessionId(sessionId);
        checkIn.setApplicationId(application.getId());
        checkIn.setCasId(casId);
        checkIn.setCheckedInAt(LocalDateTime.now());
        checkInMapper.initializeSequence(sessionId);
        int queueNumber =
                checkInMapper.selectNextNumberForUpdate(sessionId);
        checkInMapper.incrementNextNumber(sessionId);
        checkIn.setQueueNumber(queueNumber);
        checkIn.setQueueOrder((long) queueNumber);
        checkIn.setPassCount(0);
        checkIn.setPriority(carryoverId != null);
        checkIn.setRequiresRecheckIn(false);
        try {
            checkInMapper.insert(checkIn);
        } catch (DuplicateKeyException exception) {
            // 同一用户并发扫码时返回已经落库的同一条签到记录。
            return ServiceResult.success(CheckInVO.from(
                    checkInMapper.selectBySessionAndApplication(
                            sessionId, application.getId()
                    )
            ));
        }
        if (carryoverId != null) {
            sessionMapper.useCarryover(carryoverId, sessionId);
        }
        interviewSseService.publishQueueAfterCommit(departmentId);
        return ServiceResult.success(CheckInVO.from(checkIn));
    }

    /** 兼容原有仅携带二维码令牌的调用。 */
    public ServiceResult<CheckInVO> checkIn(String token, String casId) {
        return checkIn(null, token, casId);
    }

}
