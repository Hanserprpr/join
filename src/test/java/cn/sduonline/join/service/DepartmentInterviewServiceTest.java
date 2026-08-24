package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.InterviewQueueStatus;
import cn.sduonline.join.data.enums.InterviewPassMode;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigPatchRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationVO;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentInterview;
import cn.sduonline.join.data.po.DepartmentInterviewRoom;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
import cn.sduonline.join.mapper.DepartmentInterviewRoomMapper;
import cn.sduonline.join.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class DepartmentInterviewServiceTest {

    @Mock AdminOrganizationMapper organizationMapper;
    @Mock DepartmentInterviewMapper interviewMapper;
    @Mock DepartmentInterviewRoomMapper roomMapper;
    @Mock TransactionTemplate transactionTemplate;
    @Mock TransactionStatus transactionStatus;
    @Mock InterviewSseService interviewSseService;
    @Mock UserMapper userMapper;
    @Mock WeChatSubscribeMessageService subscribeMessageService;
    @Mock AuthorizationService authorizationService;
    private WeChatProperties weChatProperties;
    private DepartmentInterviewService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        weChatProperties = new WeChatProperties();
        service = new DepartmentInterviewService(
                organizationMapper, interviewMapper, roomMapper,
                transactionTemplate,
                interviewSseService, userMapper, subscribeMessageService,
                weChatProperties, authorizationService
        );
    }

    @Test
    void notifiesCalledCandidateUsingRoomName() {
        DepartmentInterviewRoom room = openRoom();
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        DepartmentInterview next = waitingCandidate();
        next.setRoomId(9L);
        when(interviewMapper.selectActiveByRoom(9L))
                .thenReturn(null, next);
        when(interviewMapper.selectNextWaitingForUpdate(12L, 5L))
                .thenReturn(next);
        when(userMapper.selectById("20240001")).thenReturn(user(
                "20240001", "张三", "candidate-openid"
        ));
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        Department department = new Department();
        department.setId(12L);
        department.setName("技术部");
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(department);

        var result = service.callNextInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        verify(subscribeMessageService).send(
                org.mockito.ArgumentMatchers.eq("candidate-openid"),
                org.mockito.ArgumentMatchers.eq(
                        weChatProperties.getInterviewCallTemplateId()
                ),
                org.mockito.ArgumentMatchers.eq(java.util.Map.of(
                        "thing2",
                        new cn.sduonline.join.client.WeChatApiClient.TemplateData(
                                "张三"
                        ),
                        "character_string14",
                        new cn.sduonline.join.client.WeChatApiClient.TemplateData(
                                "7"
                        ),
                        "thing23",
                        new cn.sduonline.join.client.WeChatApiClient.TemplateData(
                                "第一面试室"
                        ),
                        "thing31",
                        new cn.sduonline.join.client.WeChatApiClient.TemplateData(
                                "技术部"
                        )
                ))
        );
        verify(userMapper, never()).selectById("admin01");
    }

    @Test
    void updatesEvaluationAndReturnsOwnEvaluation() {
        DepartmentInterview saved = waitingCandidate();
        saved.setId(300L);
        saved.setRoomId(9L);
        when(interviewMapper.selectByIdAndDepartment(12L, 300L))
                .thenReturn(saved);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        InterviewEvaluationVO own = new InterviewEvaluationVO(
                300L, "admin01", "管理员一", 4, "沟通清晰", null
        );
        when(roomMapper.selectEvaluation(300L, "admin01")).thenReturn(own);

        var result = service.updateEvaluation(
                12L, 300L, "admin01",
                new InterviewEvaluationRequest(4, " 沟通清晰 ")
        );

        assertTrue(result.isSuccess());
        assertEquals(4, result.data().score());
        assertEquals("沟通清晰", result.data().evaluation());
        verify(roomMapper).upsertEvaluation(300L, "admin01", 4, "沟通清晰");
    }

    @Test
    void cannotUpdateEvaluationUnlessRoomMember() {
        DepartmentInterview saved = waitingCandidate();
        saved.setId(300L);
        saved.setRoomId(9L);
        when(interviewMapper.selectByIdAndDepartment(12L, 300L))
                .thenReturn(saved);
        when(roomMapper.countMember(9L, "someone-else")).thenReturn(0);

        var result = service.updateEvaluation(
                12L, 300L, "someone-else",
                new InterviewEvaluationRequest(5, "他人评价")
        );

        assertEquals(BizCode.NO_PERMISSION, result.error());
        verify(roomMapper, never()).upsertEvaluation(
                any(), any(), any(), any()
        );
    }

    @Test
    void queueIncludesCandidateInterviewingElsewhereWithoutPrivateDetails() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        InterviewQueueItemVO item = new InterviewQueueItemVO(
                200L, 100L, "20240001", "张三", 7, 7L, 0, null,
                InterviewQueueStatus.INTERVIEWING_ELSEWHERE,
                null, null, null, null, false
        );
        when(interviewMapper.selectQueue(12L)).thenReturn(List.of(item));

        var result = service.findQueue(12L);

        assertTrue(result.isSuccess());
        assertEquals(
                InterviewQueueStatus.INTERVIEWING_ELSEWHERE,
                result.data().getFirst().status()
        );
        assertEquals(null, result.data().getFirst().interviewerCasId());
        assertEquals(null, result.data().getFirst().interviewerName());
    }

    @Test
    void candidateSeesPeopleAheadAndCurrentlyInterviewingNumbers() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        InterviewQueueItemVO item = new InterviewQueueItemVO(
                200L, 100L, "20240001", "张三", 12, 12L, 0, null,
                InterviewQueueStatus.WAITING,
                null, null, null, null, false
        );
        when(interviewMapper.selectCandidateQueueItem(12L, "20240001"))
                .thenReturn(item);
        when(interviewMapper.countPeopleAhead(12L, 12L, false))
                .thenReturn(3);
        when(interviewMapper.selectInterviewingQueueNumbers(12L))
                .thenReturn(List.of(8, 9));

        var result = service.findMyQueueStatus(12L, "20240001");

        assertTrue(result.isSuccess());
        assertEquals(12, result.data().queueNumber());
        assertEquals(3, result.data().peopleAhead());
        assertEquals(List.of(8, 9), result.data().interviewingQueueNumbers());
    }

    @Test
    void candidateMustCheckInBeforeViewingQueueStatus() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(interviewMapper.selectCandidateQueueItem(12L, "20240001"))
                .thenReturn(null);

        var result = service.findMyQueueStatus(12L, "20240001");

        assertEquals(BizCode.CHECK_IN_NOT_FOUND, result.error());
        verify(interviewMapper, never()).countPeopleAhead(
                any(), any(), any()
        );
    }

    @Test
    void passingCurrentCandidateMovesThemBackThreePlaces() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(openRoom());
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);
        when(interviewMapper.selectQueueConfig(12L))
                .thenReturn(new InterviewQueueConfigVO(3, 2));
        DepartmentCheckIn target = checkIn(200L, 1L, 0);
        when(interviewMapper.selectCheckInForUpdate(200L))
                .thenReturn(target);
        DepartmentCheckIn second = checkIn(201L, 2L, 0);
        DepartmentCheckIn third = checkIn(202L, 3L, 0);
        DepartmentCheckIn fourth = checkIn(203L, 4L, 0);
        when(interviewMapper.selectReorderableQueueForUpdate(12L))
                .thenReturn(List.of(target, second, third, fourth));
        InterviewQueueItemVO moved = new InterviewQueueItemVO(
                200L, 100L, "20240001", "张三",
                7, 4L, 1, null, InterviewQueueStatus.WAITING,
                null, null, null, null, false
        );
        when(interviewMapper.selectCandidateQueueItem(12L, "20240001"))
                .thenReturn(moved);

        var result = service.passCurrentInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(1, result.data().passCount());
        assertEquals(4L, target.getQueueOrder());
        verify(interviewMapper).deleteActive(300L);
        verify(interviewMapper).deleteInterview(300L);
        verify(interviewSseService).publishQueue(12L);
        verify(interviewSseService).publishRoom(12L, 9L);
    }

    @Test
    void recheckInModeRemovesPassedCandidateUntilTheyCheckInAgain() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(openRoom());
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);
        when(interviewMapper.selectQueueConfig(12L)).thenReturn(
                new InterviewQueueConfigVO(
                        3, 2, InterviewPassMode.RECHECK_IN
                )
        );
        DepartmentCheckIn target = checkIn(200L, 1L, 0);
        when(interviewMapper.selectCheckInForUpdate(200L)).thenReturn(target);

        var result = service.passCurrentInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        assertTrue(target.getRequiresRecheckIn());
        assertEquals(1, target.getPassCount());
        verify(interviewMapper).requireCheckInAgain(target);
        verify(interviewMapper, never()).selectReorderableQueueForUpdate(any());
    }

    @Test
    void rejectsPassAfterDepartmentLimitIsReached() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(openRoom());
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);
        when(interviewMapper.selectQueueConfig(12L))
                .thenReturn(new InterviewQueueConfigVO(3, 2));
        when(interviewMapper.selectCheckInForUpdate(200L))
                .thenReturn(checkIn(200L, 1L, 2));

        var result = service.passCurrentInRoom(12L, 9L, "admin01");

        assertEquals(BizCode.INTERVIEW_PASS_LIMIT_REACHED, result.error());
        verify(interviewMapper, never()).deleteActive(any());
    }

    @Test
    void stoppingCallingReleasesCandidateWithoutChangingQueueOrPassCount() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(openRoom());
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);
        InterviewQueueItemVO waiting = new InterviewQueueItemVO(
                200L, 100L, "20240001", "张三",
                7, 1L, 0, null, InterviewQueueStatus.WAITING,
                null, null, null, null, false
        );
        when(interviewMapper.selectCandidateQueueItem(12L, "20240001"))
                .thenReturn(waiting);

        var result = service.stopCallingInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(InterviewQueueStatus.WAITING, result.data().status());
        assertEquals(0, result.data().passCount());
        assertEquals(1L, result.data().queueOrder());
        verify(interviewMapper).deleteActive(300L);
        verify(interviewMapper).deleteInterview(300L);
        verify(interviewMapper, never()).updateCheckInQueue(any());
        verify(interviewSseService).publishQueue(12L);
        verify(interviewSseService).publishRoom(12L, 9L);
    }

    @Test
    void rejectsStoppingCallingWhenRoomHasNoActiveInterview() {
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(openRoom());
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(null);

        var result = service.stopCallingInRoom(12L, 9L, "admin01");

        assertEquals(BizCode.INTERVIEW_NOT_ACTIVE, result.error());
        verify(interviewMapper, never()).deleteActive(any());
        verify(interviewSseService, never()).publishQueue(any());
        verify(interviewSseService, never()).publishRoom(any(), any());
    }

    @Test
    void partiallyUpdatesOnlyProvidedQueueConfigField() {
        when(interviewMapper.selectQueueConfig(12L))
                .thenReturn(new InterviewQueueConfigVO(3, 2));

        var result = service.patchQueueConfig(
                12L, new InterviewQueueConfigPatchRequest(null, 5)
        );

        assertTrue(result.isSuccess());
        assertEquals(3, result.data().passDelayCount());
        assertEquals(5, result.data().maxPassCount());
        verify(interviewMapper).updateQueueConfig(
                12L, 3, 5, InterviewPassMode.DELAY
        );
    }

    @Test
    void rejectsEmptyQueueConfigPatch() {
        var result = service.patchQueueConfig(
                12L, new InterviewQueueConfigPatchRequest(null, null)
        );

        assertEquals(BizCode.PARAM_INVALID, result.error());
        verify(interviewMapper, never()).updateQueueConfig(
                any(), any(Integer.class), any(Integer.class), any()
        );
    }

    @Test
    void roomMembersShareTheSameCurrentCandidate() {
        DepartmentInterviewRoom room = openRoom();
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(roomMapper.countMember(9L, "admin02")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);

        var first = service.findCurrentInRoom(12L, 9L, "admin01");
        var second = service.findCurrentInRoom(12L, 9L, "admin02");

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.data().id(), second.data().id());
        assertEquals("20240001", second.data().candidateCasId());
    }

    @Test
    void callNextLocksRoomBeforeCheckingAndAssigningCandidate() {
        DepartmentInterviewRoom room = openRoom();
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        DepartmentInterview next = waitingCandidate();
        next.setRoomId(9L);
        when(interviewMapper.selectActiveByRoom(9L))
                .thenReturn(null, next);
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        when(interviewMapper.selectNextWaitingForUpdate(12L, 5L))
                .thenReturn(next);

        var result = service.callNextInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(9L, result.data().currentInterview().roomId());
        verify(roomMapper).selectByIdForUpdate(12L, 9L);
        verify(interviewMapper).insertRoomActive(next);
    }

    @Test
    void secondCallReportsAdministratorsWithPendingEvaluations() {
        DepartmentInterviewRoom room = openRoom();
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin02")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);
        when(roomMapper.selectMemberStatuses(9L, 300L)).thenReturn(List.of(
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin01", "李老师", true, java.time.LocalDateTime.now()
                ),
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin02", "王老师", false, null
                )
        ));

        var result = service.callNextInRoom(12L, 9L, "admin02");

        assertEquals(BizCode.INTERVIEW_EVALUATIONS_PENDING, result.error());
        assertEquals(
                "admin02",
                result.data().pendingAdministrators().getFirst().casId()
        );
        assertEquals(false, result.data().canForce());
        verify(interviewMapper, never()).selectNextWaitingForUpdate(
                any(), any()
        );
    }

    @Test
    void administratorSubmitsIndependentEvaluation() {
        DepartmentInterviewRoom room = openRoom();
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L)).thenReturn(active);
        when(roomMapper.selectMemberStatuses(9L, 300L)).thenReturn(List.of(
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin01", "李老师", true, java.time.LocalDateTime.now()
                ),
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin02", "王老师", false, null
                )
        ));

        var result = service.submitEvaluationInRoom(
                12L, 9L, "admin01",
                new InterviewEvaluationRequest(5, " 表现优秀 ")
        );

        assertTrue(result.isSuccess());
        assertEquals(1, result.data().submittedCount());
        assertEquals(2, result.data().administratorCount());
        assertEquals(true, result.data().currentUserSubmitted());
        verify(roomMapper).upsertEvaluation(
                300L, "admin01", 5, "表现优秀"
        );
        verify(interviewSseService).publishRoom(12L, 9L);
    }

    @Test
    void allEvaluationsAllowFinishingAndCallingNextAtomically() {
        DepartmentInterviewRoom room = openRoom();
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        DepartmentInterview next = waitingCandidate();
        next.setId(301L);
        next.setCheckInId(201L);
        next.setCandidateCasId("20240002");
        next.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L))
                .thenReturn(active, active, next);
        var submittedAt = java.time.LocalDateTime.now();
        when(roomMapper.selectMemberStatuses(9L, 300L)).thenReturn(List.of(
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin01", "李老师", true, submittedAt
                ),
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin02", "王老师", true, submittedAt
                )
        ));
        when(roomMapper.selectMemberStatuses(9L, 301L)).thenReturn(List.of(
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin01", "李老师", false, null
                ),
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin02", "王老师", false, null
                )
        ));
        when(interviewMapper.selectNextWaitingForUpdate(12L, 5L))
                .thenReturn(next);

        var result = service.callNextInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(301L, result.data().currentInterview().id());
        verify(interviewMapper).finishInterview(
                org.mockito.ArgumentMatchers.eq(300L), any()
        );
        verify(interviewMapper).deleteActive(300L);
        verify(interviewMapper).insertRoomActive(next);
    }

    @Test
    void roomCreatorCanForceFinishWithPendingEvaluations() {
        DepartmentInterviewRoom room = openRoom();
        room.setCreatedBy("admin01");
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setRoomId(9L);
        when(roomMapper.selectByIdForUpdate(12L, 9L)).thenReturn(room);
        when(roomMapper.selectById(12L, 9L)).thenReturn(room);
        when(roomMapper.countMember(9L, "admin01")).thenReturn(1);
        when(interviewMapper.selectActiveByRoom(9L))
                .thenReturn(active, active, null);
        when(roomMapper.selectMemberStatuses(9L, 300L)).thenReturn(List.of(
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin01", "李老师", true, java.time.LocalDateTime.now()
                ),
                new cn.sduonline.join.data.dto.InterviewRoomMemberStatusVO(
                        "admin02", "王老师", false, null
                )
        ));

        var result = service.forceFinishInRoom(12L, 9L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(null, result.data().currentInterview());
        verify(interviewMapper).deleteActive(300L);
    }

    private static DepartmentInterview waitingCandidate() {
        DepartmentInterview interview = new DepartmentInterview();
        interview.setDepartmentId(12L);
        interview.setCheckInId(200L);
        interview.setApplicationId(100L);
        interview.setCandidateCasId("20240001");
        interview.setCandidateName("张三");
        interview.setQueueNumber(7);
        return interview;
    }

    private static DepartmentCheckIn checkIn(
            Long id,
            Long queueOrder,
            int passCount
    ) {
        DepartmentCheckIn checkIn = new DepartmentCheckIn();
        checkIn.setId(id);
        checkIn.setDepartmentId(12L);
        checkIn.setApplicationId(100L);
        checkIn.setCasId(id.equals(200L) ? "20240001" : "user-" + id);
        checkIn.setQueueNumber(queueOrder.intValue());
        checkIn.setQueueOrder(queueOrder);
        checkIn.setPassCount(passCount);
        return checkIn;
    }

    private static DepartmentInterviewRoom openRoom() {
        DepartmentInterviewRoom room = new DepartmentInterviewRoom();
        room.setId(9L);
        room.setDepartmentId(12L);
        room.setSessionId(5L);
        room.setName("第一面试室");
        room.setStatus("OPEN");
        return room;
    }

    private static User user(
            String casId,
            String name,
            String openId
    ) {
        User user = new User();
        user.setCasId(casId);
        user.setName(name);
        user.setWechatOpenid(openId);
        return user;
    }
}
