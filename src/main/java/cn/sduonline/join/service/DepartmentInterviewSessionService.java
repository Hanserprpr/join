package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.InterviewSessionRequest;
import cn.sduonline.join.data.dto.InterviewSessionVO;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.InterviewSessionStatus;
import cn.sduonline.join.data.po.DepartmentInterviewSession;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepartmentInterviewSessionService {

    private static final int DEFAULT_QR_CODE_TTL_SECONDS = 8;

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentInterviewSessionMapper sessionMapper;

    public ServiceResult<InterviewSessionVO> create(
            Long departmentId,
            InterviewSessionRequest request
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            return ServiceResult.failure(BizCode.PARAM_INVALID);
        }
        DepartmentInterviewSession session = fromRequest(
                departmentId, null, request
        );
        session.setStatus(InterviewSessionStatus.DRAFT);
        sessionMapper.insert(session);
        return ServiceResult.success(InterviewSessionVO.from(session));
    }

    public ServiceResult<InterviewSessionVO> update(
            Long departmentId,
            Long sessionId,
            InterviewSessionRequest request
    ) {
        if (!request.endsAt().isAfter(request.startsAt())) {
            return ServiceResult.failure(BizCode.PARAM_INVALID);
        }
        DepartmentInterviewSession session = fromRequest(
                departmentId, sessionId, request
        );
        if (sessionMapper.update(session) == 0) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        return findById(departmentId, sessionId);
    }

    @Transactional
    public ServiceResult<InterviewSessionVO> publish(
            Long departmentId,
            Long sessionId
    ) {
        if (sessionMapper.publish(
                departmentId, sessionId, LocalDateTime.now()
        ) == 0) {
            return ServiceResult.failure(
                    BizCode.INTERVIEW_SESSION_STATE_INVALID
            );
        }
        return findById(departmentId, sessionId);
    }

    @Transactional
    public ServiceResult<InterviewSessionVO> end(
            Long departmentId,
            Long sessionId
    ) {
        if (sessionMapper.selectPublishedForUpdate(
                departmentId, sessionId
        ) == null) {
            return ServiceResult.failure(
                    BizCode.INTERVIEW_SESSION_STATE_INVALID
            );
        }
        if (sessionMapper.countActiveInterviews(sessionId) > 0) {
            return ServiceResult.failure(
                    BizCode.INTERVIEW_SESSION_STATE_INVALID
            );
        }
        if (sessionMapper.end(
                departmentId, sessionId, LocalDateTime.now()
        ) == 0) {
            return ServiceResult.failure(
                    BizCode.INTERVIEW_SESSION_STATE_INVALID
            );
        }
        sessionMapper.cancelUnusedCarryovers(sessionId);
        sessionMapper.createCarryovers(sessionId);
        return findById(departmentId, sessionId);
    }

    public ServiceResult<List<InterviewSessionVO>> findPublished(
            Long departmentId
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(sessionMapper.selectPublished(departmentId)
                .stream().map(InterviewSessionVO::from).toList());
    }

    public ServiceResult<List<InterviewSessionVO>> findAll(Long departmentId) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(sessionMapper.selectAll(departmentId)
                .stream().map(InterviewSessionVO::from).toList());
    }

    private ServiceResult<InterviewSessionVO> findById(
            Long departmentId,
            Long sessionId
    ) {
        DepartmentInterviewSession session =
                sessionMapper.selectById(departmentId, sessionId);
        return session == null
                ? ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_FOUND)
                : ServiceResult.success(InterviewSessionVO.from(session));
    }

    private static DepartmentInterviewSession fromRequest(
            Long departmentId,
            Long sessionId,
            InterviewSessionRequest request
    ) {
        DepartmentInterviewSession session = new DepartmentInterviewSession();
        session.setId(sessionId);
        session.setDepartmentId(departmentId);
        session.setName(request.name().trim());
        session.setStartsAt(request.startsAt());
        session.setEndsAt(request.endsAt());
        session.setLocation(request.location().trim());
        session.setCheckInLimit(request.checkInLimit());
        session.setQrCheckInEnabled(Boolean.TRUE.equals(
                request.qrCheckInEnabled()
        ));
        session.setQrCodeTtlSeconds(request.qrCodeTtlSeconds() == null
                ? DEFAULT_QR_CODE_TTL_SECONDS
                : request.qrCodeTtlSeconds());
        return session;
    }
}
