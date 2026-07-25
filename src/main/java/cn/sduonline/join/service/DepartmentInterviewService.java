package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.DepartmentInterviewVO;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.MyInterviewQueueStatusVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigRequest;
import cn.sduonline.join.data.dto.InterviewQueueConfigPatchRequest;
import cn.sduonline.join.data.enums.InterviewQueueStatus;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentInterview;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DepartmentInterviewService {

    private static final int MAX_ASSIGNMENT_ATTEMPTS = 3;

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentInterviewMapper interviewMapper;
    private final TransactionTemplate transactionTemplate;
    private final InterviewSseService interviewSseService;

    public ServiceResult<DepartmentInterviewVO> callNext(
            Long departmentId,
            String interviewerCasId
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        for (int attempt = 0; attempt < MAX_ASSIGNMENT_ATTEMPTS; attempt++) {
            try {
                ServiceResult<DepartmentInterviewVO> result =
                        transactionTemplate.execute(status ->
                        assignNext(departmentId, interviewerCasId)
                );
                if (result != null && result.isSuccess()) {
                    interviewSseService.publish(departmentId);
                }
                return result;
            } catch (DuplicateKeyException exception) {
                // 其他管理员可能刚刚占用了同一应试者，重新取队首。
            }
        }
        DepartmentInterview active =
                interviewMapper.selectActiveByInterviewer(interviewerCasId);
        return ServiceResult.failure(
                active == null
                        ? BizCode.TOO_MANY_REQUESTS
                        : BizCode.INTERVIEW_ADMIN_BUSY
        );
    }

    public ServiceResult<DepartmentInterviewVO> findCurrent(
            Long departmentId,
            String interviewerCasId
    ) {
        DepartmentInterview active =
                interviewMapper.selectActiveByInterviewer(interviewerCasId);
        if (active == null
                || !departmentId.equals(active.getDepartmentId())) {
            return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
        }
        return ServiceResult.success(DepartmentInterviewVO.from(active));
    }

    public ServiceResult<List<InterviewQueueItemVO>> findQueue(
            Long departmentId
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(interviewMapper.selectQueue(departmentId));
    }

    public ServiceResult<MyInterviewQueueStatusVO> findMyQueueStatus(
            Long departmentId,
            String casId
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        InterviewQueueItemVO item =
                interviewMapper.selectCandidateQueueItem(departmentId, casId);
        if (item == null) {
            return ServiceResult.failure(BizCode.CHECK_IN_NOT_FOUND);
        }
        int peopleAhead = item.status() == InterviewQueueStatus.WAITING
                || item.status() == InterviewQueueStatus.INTERVIEWING_ELSEWHERE
                ? interviewMapper.countPeopleAhead(
                        departmentId, item.queueOrder()
                )
                : 0;
        return ServiceResult.success(new MyInterviewQueueStatusVO(
                departmentId,
                item.queueNumber(),
                item.status(),
                peopleAhead,
                interviewMapper.selectInterviewingQueueNumbers(departmentId)
        ));
    }

    public ServiceResult<InterviewQueueConfigVO> findQueueConfig(
            Long departmentId
    ) {
        InterviewQueueConfigVO config =
                interviewMapper.selectQueueConfig(departmentId);
        return config == null
                ? ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND)
                : ServiceResult.success(config);
    }

    public ServiceResult<InterviewQueueConfigVO> updateQueueConfig(
            Long departmentId,
            InterviewQueueConfigRequest request
    ) {
        if (interviewMapper.updateQueueConfig(
                departmentId,
                request.passDelayCount(),
                request.maxPassCount()
        ) == 0) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(new InterviewQueueConfigVO(
                request.passDelayCount(), request.maxPassCount()
        ));
    }

    public ServiceResult<InterviewQueueConfigVO> patchQueueConfig(
            Long departmentId,
            InterviewQueueConfigPatchRequest request
    ) {
        if (request.passDelayCount() == null
                && request.maxPassCount() == null) {
            return ServiceResult.failure(BizCode.PARAM_INVALID);
        }
        InterviewQueueConfigVO current =
                interviewMapper.selectQueueConfig(departmentId);
        if (current == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        int passDelayCount = request.passDelayCount() == null
                ? current.passDelayCount()
                : request.passDelayCount();
        int maxPassCount = request.maxPassCount() == null
                ? current.maxPassCount()
                : request.maxPassCount();
        interviewMapper.updateQueueConfig(
                departmentId, passDelayCount, maxPassCount
        );
        return ServiceResult.success(new InterviewQueueConfigVO(
                passDelayCount, maxPassCount
        ));
    }

    public ServiceResult<InterviewQueueItemVO> passCurrent(
            Long departmentId,
            String interviewerCasId
    ) {
        ServiceResult<InterviewQueueItemVO> result =
                transactionTemplate.execute(status ->
                        passCurrentInTransaction(
                                departmentId, interviewerCasId
                        )
                );
        if (result != null && result.isSuccess()) {
            interviewSseService.publish(departmentId);
        }
        return result;
    }

    public ServiceResult<DepartmentInterviewVO> finish(
            Long departmentId,
            String interviewerCasId
    ) {
        ServiceResult<DepartmentInterviewVO> result =
                transactionTemplate.execute(status -> {
            DepartmentInterview active =
                    interviewMapper.selectActiveByInterviewer(interviewerCasId);
            if (active == null
                    || !departmentId.equals(active.getDepartmentId())) {
                return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
            }
            LocalDateTime endedAt = LocalDateTime.now();
            interviewMapper.finishInterview(active.getId(), endedAt);
            interviewMapper.deleteActive(active.getId());
            active.setEndedAt(endedAt);
            return ServiceResult.success(DepartmentInterviewVO.from(active));
        });
        if (result != null && result.isSuccess()) {
            interviewSseService.publish(departmentId);
        }
        return result;
    }

    private ServiceResult<DepartmentInterviewVO> assignNext(
            Long departmentId,
            String interviewerCasId
    ) {
        DepartmentInterview current =
                interviewMapper.selectActiveByInterviewer(interviewerCasId);
        if (current != null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ADMIN_BUSY);
        }
        DepartmentInterview next =
                interviewMapper.selectNextWaitingForUpdate(departmentId);
        if (next == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_QUEUE_EMPTY);
        }
        next.setInterviewerCasId(interviewerCasId);
        next.setStartedAt(LocalDateTime.now());
        interviewMapper.insertInterview(next);
        interviewMapper.insertActive(next);
        return ServiceResult.success(DepartmentInterviewVO.from(next));
    }

    private ServiceResult<InterviewQueueItemVO> passCurrentInTransaction(
            Long departmentId,
            String interviewerCasId
    ) {
        DepartmentInterview active =
                interviewMapper.selectActiveByInterviewer(interviewerCasId);
        if (active == null
                || !departmentId.equals(active.getDepartmentId())) {
            return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
        }
        InterviewQueueConfigVO config =
                interviewMapper.selectQueueConfig(departmentId);
        DepartmentCheckIn target =
                interviewMapper.selectCheckInForUpdate(active.getCheckInId());
        if (target.getPassCount() >= config.maxPassCount()) {
            return ServiceResult.failure(
                    BizCode.INTERVIEW_PASS_LIMIT_REACHED
            );
        }

        interviewMapper.deleteActive(active.getId());
        interviewMapper.deleteInterview(active.getId());
        List<DepartmentCheckIn> queue =
                new java.util.ArrayList<>(
                        interviewMapper.selectReorderableQueueForUpdate(
                                departmentId
                        )
                );
        int currentIndex = java.util.stream.IntStream.range(0, queue.size())
                .filter(index -> queue.get(index).getId()
                        .equals(target.getId()))
                .findFirst()
                .orElseThrow();
        queue.remove(currentIndex);
        int newIndex = Math.min(
                currentIndex + config.passDelayCount(), queue.size()
        );
        target.setPassCount(target.getPassCount() + 1);
        queue.add(newIndex, target);
        for (int index = 0; index < queue.size(); index++) {
            DepartmentCheckIn checkIn = queue.get(index);
            checkIn.setQueueOrder((long) index + 1);
            interviewMapper.updateCheckInQueue(checkIn);
        }
        return ServiceResult.success(
                interviewMapper.selectCandidateQueueItem(
                        departmentId, target.getCasId()
                )
        );
    }
}
