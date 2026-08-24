package cn.sduonline.join.service;

import cn.sduonline.join.config.AppProperties;
import cn.sduonline.join.data.dto.CheckInQrCodeVO;
import cn.sduonline.join.data.dto.CheckInQrGrant;
import cn.sduonline.join.data.dto.CheckInVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.data.po.DepartmentCheckIn;
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
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        DepartmentInterviewSession session =
                sessionMapper.selectPublished(departmentId);
        if (session == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_OPEN);
        }
        if (!Boolean.TRUE.equals(session.getQrCheckInEnabled())) {
            return ServiceResult.failure(BizCode.STATE_NOT_ALLOWED);
        }
        long ttlSeconds = session.getQrCodeTtlSeconds() == null
                ? appProperties.getCheckIn().getTokenTtlSeconds()
                : session.getQrCodeTtlSeconds();
        ttlSeconds = Math.max(5, ttlSeconds);
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
        String content = appProperties.getCheckIn().getEntryUrl()
                + "?token=" + token;
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
        boolean scannedWithQr = token != null && !token.isBlank();
        if (scannedWithQr) {
            CheckInQrGrant grant = resolveQrGrant(token);
            if (grant == null) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            departmentId = grant.departmentId();
            sessionId = grant.sessionId();
            if (requestedDepartmentId != null
                    && !requestedDepartmentId.equals(departmentId)) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            // 令牌本身已绑定部门和场次，因此切换开关期间已展示的二维码
            // 仍可完成签到；仅开启二维码模式时才享有跨场次优先资格。
        } else {
            if (requestedDepartmentId == null) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            departmentId = requestedDepartmentId;
            if (organizationMapper.selectDepartmentById(departmentId) == null) {
                return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
            }
            DepartmentInterviewSession published = sessionMapper.selectPublished(departmentId);
            if (published == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_OPEN);
            }
            if (Boolean.TRUE.equals(published.getQrCheckInEnabled())) {
                return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
            }
            sessionId = published.getId();
        }
        return completeCheckIn(
                departmentId, sessionId, scannedWithQr, casId);
    }

    /**
     * 在扫码当下验证短效二维码，用于跨越后续的微信 OAuth。
     */
    public ServiceResult<CheckInQrGrant> captureQrGrant(String token) {
        CheckInQrGrant grant = resolveQrGrant(token);
        return grant == null
                ? ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID)
                : ServiceResult.success(grant);
    }

    /** 使用扫码时已验证的凭证完成签到。 */
    @Transactional
    public ServiceResult<CheckInVO> checkInCaptured(
            CheckInQrGrant grant, String casId
    ) {
        if (grant == null || grant.departmentId() == null
                || grant.sessionId() == null) {
            return ServiceResult.failure(BizCode.CHECK_IN_TOKEN_INVALID);
        }
        return completeCheckIn(
                grant.departmentId(), grant.sessionId(), true, casId);
    }

    private ServiceResult<CheckInVO> completeCheckIn(
            Long departmentId,
            Long sessionId,
            boolean scannedWithQr,
            String casId
    ) {
        DepartmentInterviewSession session =
                sessionMapper.selectPublishedForUpdate(
                        departmentId, sessionId
                );
        if (session == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_OPEN);
        }
        boolean priorityEligible = scannedWithQr
                && Boolean.TRUE.equals(session.getQrCheckInEnabled());
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

    private CheckInQrGrant resolveQrGrant(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String value = redisTemplate.opsForValue().get(
                TOKEN_KEY_PREFIX + token);
        if (value == null) {
            return null;
        }
        try {
            String[] parts = value.split(":", 2);
            return new CheckInQrGrant(
                    Long.valueOf(parts[0]), Long.valueOf(parts[1]));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** 兼容原有仅携带二维码令牌的调用。 */
    public ServiceResult<CheckInVO> checkIn(String token, String casId) {
        return checkIn(null, token, casId);
    }

    /**
     * 取消当前用户在部门当前已发布场次的签到。
     * 主动取消不会恢复已经使用的顺延优先资格。
     */
    @Transactional
    public ServiceResult<Void> cancelCheckIn(Long departmentId, String casId) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        DepartmentCheckIn checkIn = checkInMapper
                .selectCurrentByDepartmentAndUserForUpdate(
                        departmentId, casId
                );
        if (checkIn == null) {
            return ServiceResult.failure(BizCode.CHECK_IN_NOT_FOUND);
        }
        if (checkInMapper.countInterviewsByCheckIn(checkIn.getId()) > 0) {
            return ServiceResult.failure(BizCode.CHECK_IN_CANNOT_CANCEL);
        }

        int deleted = checkInMapper.deleteOwnedCheckIn(
                checkIn.getId(), departmentId, casId
        );
        if (deleted != 1) {
            throw new IllegalStateException("Check-in changed while cancelling");
        }
        interviewSseService.publishQueueAfterCommit(departmentId);
        return ServiceResult.success(null);
    }

}
