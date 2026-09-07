package cn.sduonline.join.service;

import cn.sduonline.join.data.dto.InterviewQueueAheadCandidateVO;

import cn.sduonline.join.client.WeChatApiClient.TemplateData;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.data.dto.DepartmentInterviewVO;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.MyInterviewQueueStatusVO;
import cn.sduonline.join.data.dto.SessionScopedSnapshot;
import cn.sduonline.join.data.dto.InterviewQueueScope;
import cn.sduonline.join.data.dto.InterviewQueueConfigVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigRequest;
import cn.sduonline.join.data.dto.InterviewQueueConfigPatchRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationVO;
import cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO;
import cn.sduonline.join.data.dto.InterviewRoomStateVO;
import cn.sduonline.join.data.enums.InterviewQueueStatus;
import cn.sduonline.join.data.enums.InterviewPassMode;
import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.po.DepartmentInterview;
import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
import cn.sduonline.join.mapper.DepartmentInterviewRoomMapper;
import cn.sduonline.join.mapper.DepartmentInterviewSessionMapper;
import cn.sduonline.join.mapper.UserMapper;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentInterviewService {

    private final AdminOrganizationMapper organizationMapper;
    private final DepartmentInterviewMapper interviewMapper;
    private final DepartmentInterviewRoomMapper roomMapper;
    private final DepartmentInterviewSessionMapper sessionMapper;
    private final TransactionTemplate transactionTemplate;
    private final InterviewSseService interviewSseService;
    private final UserMapper userMapper;
    private final WeChatTemplateMessageService templateMessageService;
    private final WeChatProperties weChatProperties;
    private final AuthorizationService authorizationService;

    public ServiceResult<InterviewRoomStateVO> callNextInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        Set<String> affectedCandidates = new LinkedHashSet<>();
        ServiceResult<InterviewRoomStateVO> result =
                transactionTemplate.execute(status -> {
            ServiceResult<DepartmentInterviewRoom> access =
                    lockOpenRoomAndCheckMember(
                    departmentId, roomId, administratorCasId
            );
            if (!access.isSuccess()) {
                return ServiceResult.failure(access.error());
            }
            DepartmentInterview active =
                    interviewMapper.selectActiveByRoom(roomId);
            if (active != null) {
                InterviewRoomStateVO state = buildRoomState(
                        departmentId, roomId, administratorCasId
                );
                if (!state.allSubmitted()) {
                    return ServiceResult.failure(
                            BizCode.INTERVIEW_EVALUATIONS_PENDING, state
                    );
                }
            }
            DepartmentInterview next =
                    interviewMapper.selectNextWaitingForUpdate(
                            departmentId, access.data().getSessionId()
                    );
            if (next == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_QUEUE_EMPTY);
            }
            if (active != null) {
                finishActive(active);
                affectedCandidates.add(active.getCandidateCasId());
            }
            next.setRoomId(roomId);
            next.setInterviewerCasId(administratorCasId);
            next.setStartedAt(LocalDateTime.now());
            interviewMapper.insertRoomInterview(next);
            interviewMapper.insertRoomActive(next);
            affectedCandidates.add(next.getCandidateCasId());
            return ServiceResult.success(buildRoomState(
                    departmentId, roomId, administratorCasId
            ));
        });
        if (result != null && result.isSuccess()) {
            publishAffectedQueues(
                    departmentId, sessionOf(result.data()), affectedCandidates);
            interviewSseService.publishRoom(departmentId, roomId);
            sendCallNotification(result.data().currentInterview());
        }
        return result;
    }

    public ServiceResult<InterviewRoomStateVO> forceCallNextInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        Set<String> affectedCandidates = new LinkedHashSet<>();
        ServiceResult<InterviewRoomStateVO> result =
                transactionTemplate.execute(status -> {
            ServiceResult<DepartmentInterviewRoom> access =
                    lockOpenRoomAndCheckMember(
                    departmentId, roomId, administratorCasId
            );
            if (!access.isSuccess()) {
                return ServiceResult.failure(access.error());
            }
            if (!canForce(departmentId, roomId, administratorCasId)) {
                return ServiceResult.failure(BizCode.NO_PERMISSION);
            }
            DepartmentInterview active =
                    interviewMapper.selectActiveByRoom(roomId);
            DepartmentInterview next =
                    interviewMapper.selectNextWaitingForUpdate(
                            departmentId, access.data().getSessionId()
                    );
            if (next == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_QUEUE_EMPTY);
            }
            if (active != null) {
                finishActive(active);
                affectedCandidates.add(active.getCandidateCasId());
            }
            next.setRoomId(roomId);
            next.setInterviewerCasId(administratorCasId);
            next.setStartedAt(LocalDateTime.now());
            interviewMapper.insertRoomInterview(next);
            interviewMapper.insertRoomActive(next);
            affectedCandidates.add(next.getCandidateCasId());
            return ServiceResult.success(buildRoomState(
                    departmentId, roomId, administratorCasId
            ));
        });
        if (result != null && result.isSuccess()) {
            publishAffectedQueues(
                    departmentId, sessionOf(result.data()), affectedCandidates);
            interviewSseService.publishRoom(departmentId, roomId);
            sendCallNotification(result.data().currentInterview());
        }
        return result;
    }

    public ServiceResult<DepartmentInterviewVO> findCurrentInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        var room = roomMapper.selectById(departmentId, roomId);
        if (room == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
        }
        if (roomMapper.countMember(roomId, administratorCasId) == 0) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_JOINED);
        }
        DepartmentInterview active = interviewMapper.selectActiveByRoom(roomId);
        return active == null
                ? ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE)
                : ServiceResult.success(DepartmentInterviewVO.from(active));
    }

    public ServiceResult<InterviewRoomStateVO> submitEvaluationInRoom(
            Long departmentId, Long roomId, String administratorCasId,
            InterviewEvaluationRequest request
    ) {
        ServiceResult<InterviewRoomStateVO> result =
                transactionTemplate.execute(status -> {
            ServiceResult<DepartmentInterviewRoom> access =
                    lockOpenRoomAndCheckMember(
                    departmentId, roomId, administratorCasId
            );
            if (!access.isSuccess()) {
                return ServiceResult.failure(access.error());
            }
            DepartmentInterview active =
                    interviewMapper.selectActiveByRoom(roomId);
            if (active == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
            }
            roomMapper.upsertEvaluation(
                    active.getId(), administratorCasId, request.score(),
                    normalizeEvaluation(request.evaluation())
            );
            return ServiceResult.success(buildRoomState(
                    departmentId, roomId, administratorCasId
            ));
        });
        if (result != null && result.isSuccess()) {
            interviewSseService.publishRoom(departmentId, roomId);
        }
        return result;
    }

    public ServiceResult<InterviewRoomStateVO> finishInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        return finishInRoom(
                departmentId, roomId, administratorCasId, false
        );
    }

    public ServiceResult<InterviewRoomStateVO> forceFinishInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        return finishInRoom(
                departmentId, roomId, administratorCasId, true
        );
    }

    private ServiceResult<InterviewRoomStateVO> finishInRoom(
            Long departmentId, Long roomId, String administratorCasId,
            boolean force
    ) {
        Set<String> affectedCandidates = new LinkedHashSet<>();
        ServiceResult<InterviewRoomStateVO> result =
                transactionTemplate.execute(status -> {
            ServiceResult<DepartmentInterviewRoom> access =
                    lockOpenRoomAndCheckMember(
                    departmentId, roomId, administratorCasId
            );
            if (!access.isSuccess()) {
                return ServiceResult.failure(access.error());
            }
            InterviewRoomStateVO state = buildRoomState(
                    departmentId, roomId, administratorCasId
            );
            if (state.currentInterview() == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
            }
            if (force && !state.canForce()) {
                return ServiceResult.failure(BizCode.NO_PERMISSION, state);
            }
            if (!force && !state.allSubmitted()) {
                return ServiceResult.failure(
                        BizCode.INTERVIEW_EVALUATIONS_PENDING, state
                );
            }
            DepartmentInterview active =
                    interviewMapper.selectActiveByRoom(roomId);
            finishActive(active);
            affectedCandidates.add(active.getCandidateCasId());
            return ServiceResult.success(buildRoomState(
                    departmentId, roomId, administratorCasId
            ));
        });
        if (result != null && result.isSuccess()) {
            publishAffectedQueues(
                    departmentId, sessionOf(result.data()), affectedCandidates);
            interviewSseService.publishRoom(departmentId, roomId);
        }
        return result;
    }

    public ServiceResult<InterviewQueueItemVO> stopCallingInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        Set<String> affectedCandidates = new LinkedHashSet<>();
        ServiceResult<InterviewQueueItemVO> result =
                transactionTemplate.execute(status -> {
            ServiceResult<DepartmentInterviewRoom> access =
                    lockOpenRoomAndCheckMember(
                    departmentId, roomId, administratorCasId
            );
            if (!access.isSuccess()) {
                return ServiceResult.failure(access.error());
            }
            DepartmentInterview active =
                    interviewMapper.selectActiveByRoom(roomId);
            if (active == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
            }
            affectedCandidates.add(active.getCandidateCasId());
            interviewMapper.deleteActive(active.getId());
            interviewMapper.deleteInterview(active.getId());
            return ServiceResult.success(
                    interviewMapper.selectCandidateQueueItem(
                            departmentId, active.getCandidateCasId()
                    )
            );
        });
        if (result != null && result.isSuccess()) {
            publishAffectedQueues(
                    departmentId, sessionOf(result.data()), affectedCandidates);
            interviewSseService.publishRoom(departmentId, roomId);
        }
        return result;
    }

    public ServiceResult<InterviewQueueItemVO> passCurrentInRoom(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        Set<String> affectedCandidates = new LinkedHashSet<>();
        ServiceResult<InterviewQueueItemVO> result =
                transactionTemplate.execute(status -> {
            ServiceResult<DepartmentInterviewRoom> access =
                    lockOpenRoomAndCheckMember(
                    departmentId, roomId, administratorCasId
            );
            if (!access.isSuccess()) {
                return ServiceResult.failure(access.error());
            }
            DepartmentInterview active =
                    interviewMapper.selectActiveByRoom(roomId);
            if (active == null) {
                return ServiceResult.failure(BizCode.INTERVIEW_NOT_ACTIVE);
            }
            affectedCandidates.add(active.getCandidateCasId());
            return passActiveInTransaction(departmentId, active);
        });
        if (result != null && result.isSuccess()) {
            publishAffectedQueues(
                    departmentId, sessionOf(result.data()), affectedCandidates);
            interviewSseService.publishRoom(departmentId, roomId);
        }
        return result;
    }

    public ServiceResult<InterviewRoomStateVO> findRoomState(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        var room = roomMapper.selectById(departmentId, roomId);
        if (room == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
        }
        if (roomMapper.countMember(roomId, administratorCasId) == 0) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_JOINED);
        }
        return ServiceResult.success(buildRoomState(
                departmentId, roomId, administratorCasId
        ));
    }

    public ServiceResult<List<InterviewEvaluationVO>> findEvaluations(
            Long departmentId, Long roomId, Long interviewId,
            String administratorCasId
    ) {
        var room = roomMapper.selectById(departmentId, roomId);
        if (room == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
        }
        if (roomMapper.countMember(roomId, administratorCasId) == 0) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_JOINED);
        }
        DepartmentInterview interview =
                interviewMapper.selectByIdAndDepartment(
                        departmentId, interviewId
                );
        if (interview == null || !roomId.equals(interview.getRoomId())) {
            return ServiceResult.failure(BizCode.INTERVIEW_NOT_FOUND);
        }
        return ServiceResult.success(
                roomMapper.selectEvaluations(roomId, interviewId)
        );
    }

    /**
     * 取快照所属场次，用于把队列推送限定在该场次的订阅者。
     * 取不到时返回 null，退化为部门级广播——多推几条总好过漏推。
     */
    private static Long sessionOf(Object snapshot) {
        return snapshot instanceof SessionScopedSnapshot scoped
                ? scoped.sessionId()
                : null;
    }

    /** 在叫号事务成功提交后，刷新当前场次及候选人其他未完成的队列。 */
    private void publishAffectedQueues(
            Long departmentId, Long sessionId, Set<String> candidateCasIds
    ) {
        Set<InterviewQueueScope> scopes = new LinkedHashSet<>();
        scopes.add(new InterviewQueueScope(departmentId, sessionId));
        if (!candidateCasIds.isEmpty()) {
            scopes.addAll(interviewMapper.selectCandidateQueueScopes(
                    candidateCasIds));
        }
        for (InterviewQueueScope scope : scopes) {
            interviewSseService.publishQueue(
                    scope.departmentId(), scope.sessionId());
        }
    }

    private InterviewRoomStateVO buildRoomState(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        var room = roomMapper.selectById(departmentId, roomId);
        DepartmentInterview active = interviewMapper.selectActiveByRoom(roomId);
        List<InterviewRoomMemberStatusVO> administrators =
                roomMapper.selectMemberStatuses(
                        roomId, active == null ? null : active.getId()
                );
        List<InterviewRoomMemberStatusVO> pending = administrators.stream()
                .filter(member -> !member.submitted())
                .toList();
        int submittedCount = administrators.size() - pending.size();
        boolean currentUserSubmitted = administrators.stream()
                .anyMatch(member -> member.casId().equals(administratorCasId)
                        && member.submitted());
        return new InterviewRoomStateVO(
                roomId, room.getSessionId(), room.getName(), room.getStatus(),
                active == null ? null : DepartmentInterviewVO.from(active),
                administrators, submittedCount, administrators.size(),
                pending, currentUserSubmitted,
                canForce(departmentId, roomId, administratorCasId)
        );
    }

    private boolean canForce(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        var room = roomMapper.selectById(departmentId, roomId);
        return room != null
                && (administratorCasId.equals(room.getCreatedBy())
                || authorizationService.canAccessWithPermission(
                        administratorCasId,
                        PermissionCode.INTERVIEW_MANAGE.code(),
                        OrgType.DEPARTMENT,
                        departmentId
                ));
    }

    private void finishActive(DepartmentInterview active) {
        if (active == null) {
            return;
        }
        LocalDateTime endedAt = LocalDateTime.now();
        interviewMapper.finishInterview(active.getId(), endedAt);
        interviewMapper.deleteActive(active.getId());
    }

    private ServiceResult<DepartmentInterviewRoom> lockOpenRoomAndCheckMember(
            Long departmentId, Long roomId, String administratorCasId
    ) {
        var room = roomMapper.selectByIdForUpdate(departmentId, roomId);
        if (room == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_FOUND);
        }
        if (!"OPEN".equals(room.getStatus())) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_CLOSED);
        }
        if (roomMapper.countMember(roomId, administratorCasId) == 0) {
            return ServiceResult.failure(BizCode.INTERVIEW_ROOM_NOT_JOINED);
        }
        return ServiceResult.success(room);
    }

    private void sendCallNotification(DepartmentInterviewVO interview) {
        if (!org.springframework.util.StringUtils.hasText(
                weChatProperties.getInterviewCallTemplateId())) {
            return;
        }
        try {
            var candidate = userMapper.selectById(interview.candidateCasId());
            if (candidate == null
                    || !org.springframework.util.StringUtils.hasText(
                            candidate.getWechatOpenid())) {
                return;
            }
            String window = resolveInterviewRoomName(interview);
            String departmentName = resolveDepartmentName(interview);
            templateMessageService.send(
                    candidate.getWechatOpenid(),
                    weChatProperties.getInterviewCallTemplateId(),
                    Map.of(
                            "thing2", new TemplateData(candidate.getName()),
                            "character_string14",
                            new TemplateData(String.valueOf(
                                    interview.queueNumber())),
                            "thing23", new TemplateData(window),
                            "thing31", new TemplateData(departmentName)
                    )
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to send interview call notification: "
                            + "candidateCasId={}, queueNumber={}",
                    interview.candidateCasId(),
                    interview.queueNumber(),
                    exception
            );
        }
    }

    private String resolveInterviewRoomName(DepartmentInterviewVO interview) {
        var room = roomMapper.selectById(
                interview.departmentId(), interview.roomId()
        );
        if (room != null
                && org.springframework.util.StringUtils.hasText(
                        room.getName())) {
            return room.getName();
        }
        return "面试室";
    }

    private String resolveDepartmentName(DepartmentInterviewVO interview) {
        var department = organizationMapper.selectDepartmentById(
                interview.departmentId()
        );
        if (department != null
                && org.springframework.util.StringUtils.hasText(
                        department.getName())) {
            return department.getName();
        }
        return "部门";
    }

    public ServiceResult<List<InterviewQueueItemVO>> findQueue(
            Long departmentId, Long sessionId
    ) {
        if (organizationMapper.selectDepartmentById(departmentId) == null) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        // 不要求场次仍在签到中：场次结束只关签到，排队的人还要继续面完，
        // 队列必须照常可看。
        if (sessionMapper.selectById(departmentId, sessionId) == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_SESSION_NOT_FOUND);
        }
        return ServiceResult.success(
                interviewMapper.selectQueue(departmentId, sessionId));
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
        // 同一部门同时只会有一条签到记录，场次直接取自本人的签到，
        // 前方人数与叫号中号码都只统计同场次（同校区）的候选人。
        Long sessionId = item.sessionId();
        List<InterviewQueueAheadCandidateVO> peopleAheadCandidates = item.status() == InterviewQueueStatus.WAITING
                || item.status() == InterviewQueueStatus.INTERVIEWING_ELSEWHERE
                ? interviewMapper.selectPeopleAheadCandidates(
                        departmentId, sessionId, item.queueOrder(),
                        Boolean.TRUE.equals(item.priority())
                )
                : List.of();
        return ServiceResult.success(new MyInterviewQueueStatusVO(
                departmentId,
                sessionId,
                item.queueNumber(),
                item.status(),
                peopleAheadCandidates.size(),
                interviewMapper.selectInterviewingQueueNumbers(
                        departmentId, sessionId),
                peopleAheadCandidates.stream()
                        .map(candidate -> new InterviewQueueAheadCandidateVO(
                                candidate.queueNumber(),
                                maskCandidateName(candidate.candidateName())))
                        .toList()
        ));
    }

    private static String maskCandidateName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name.strip();
        int firstCharacterEnd = normalized.offsetByCodePoints(0, 1);
        int length = normalized.codePointCount(0, normalized.length());
        return normalized.substring(0, firstCharacterEnd) + "*".repeat(length - 1);
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
        InterviewPassMode passMode = request.passMode() == null
                ? InterviewPassMode.DELAY
                : request.passMode();
        if (interviewMapper.updateQueueConfig(
                departmentId,
                request.passDelayCount(),
                request.maxPassCount(),
                passMode
        ) == 0) {
            return ServiceResult.failure(BizCode.DEPARTMENT_NOT_FOUND);
        }
        return ServiceResult.success(new InterviewQueueConfigVO(
                request.passDelayCount(), request.maxPassCount(),
                passMode
        ));
    }

    public ServiceResult<InterviewQueueConfigVO> patchQueueConfig(
            Long departmentId,
            InterviewQueueConfigPatchRequest request
    ) {
        if (request.passDelayCount() == null
                && request.maxPassCount() == null
                && request.passMode() == null) {
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
        InterviewPassMode passMode = request.passMode() == null
                ? current.passMode()
                : request.passMode();
        interviewMapper.updateQueueConfig(
                departmentId, passDelayCount, maxPassCount, passMode
        );
        return ServiceResult.success(new InterviewQueueConfigVO(
                passDelayCount, maxPassCount, passMode
        ));
    }

    public ServiceResult<DepartmentInterviewVO> findById(
            Long departmentId,
            Long interviewId
    ) {
        DepartmentInterview interview =
                interviewMapper.selectByIdAndDepartment(
                        departmentId, interviewId
                );
        return interview == null
                ? ServiceResult.failure(BizCode.INTERVIEW_NOT_FOUND)
                : ServiceResult.success(DepartmentInterviewVO.from(interview));
    }

    /**
     * 根据用户 ID 查询其在指定部门的所有面试官评价。
     */
    public ServiceResult<List<InterviewEvaluationVO>> findEvaluationsByUserId(
            Long departmentId,
            String userId
    ) {
        return ServiceResult.success(
                roomMapper.selectEvaluationsByCandidate(
                        departmentId, userId
                )
        );
    }

    /**
     * 补写或修改当前用户对指定面试的评价。
     * 一个面试有多位面试官各自提交的评价，本接口只更新当前用户的那一份。
     *
     * @param departmentId 部门 ID
     * @param interviewId 面试记录 ID
     * @param casId 当前用户学号
     * @param request 评分和评价
     * @return 当前用户对该面试的评价
     */
    public ServiceResult<InterviewEvaluationVO> updateEvaluation(
            Long departmentId,
            Long interviewId,
            String casId,
            InterviewEvaluationRequest request
    ) {
        DepartmentInterview interview =
                interviewMapper.selectByIdAndDepartment(
                        departmentId, interviewId
                );
        if (interview == null) {
            return ServiceResult.failure(BizCode.INTERVIEW_NOT_FOUND);
        }
        if (interview.getRoomId() == null
                || roomMapper.countMember(interview.getRoomId(), casId) == 0) {
            return ServiceResult.failure(BizCode.NO_PERMISSION);
        }
        roomMapper.upsertEvaluation(
                interviewId,
                casId,
                request.score(),
                normalizeEvaluation(request.evaluation())
        );
        return ServiceResult.success(
                roomMapper.selectEvaluation(interviewId, casId)
        );
    }

    private static String normalizeEvaluation(String evaluation) {
        if (evaluation == null) {
            return null;
        }
        String normalized = evaluation.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ServiceResult<InterviewQueueItemVO> passActiveInTransaction(
            Long departmentId,
            DepartmentInterview active
    ) {
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
        target.setPriority(false);
        if (config.passMode() == InterviewPassMode.RECHECK_IN) {
            target.setPassCount(target.getPassCount() + 1);
            target.setRequiresRecheckIn(true);
            interviewMapper.requireCheckInAgain(target);
            return ServiceResult.success(
                    interviewMapper.selectCandidateQueueItem(
                            departmentId, target.getCasId()
                    )
            );
        }
        List<DepartmentCheckIn> queue =
                new java.util.ArrayList<>(
                        interviewMapper.selectReorderableQueueForUpdate(
                                departmentId, target.getSessionId()
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
