package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.enums.InterviewQueueStatus;
import cn.sduonline.join.data.dto.InterviewQueueItemVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigVO;
import cn.sduonline.join.data.dto.InterviewQueueConfigPatchRequest;
import cn.sduonline.join.data.dto.InterviewEvaluationRequest;
import cn.sduonline.join.data.po.DepartmentCheckIn;
import cn.sduonline.join.data.po.Department;
import cn.sduonline.join.data.po.DepartmentInterview;
import cn.sduonline.join.data.po.User;
import cn.sduonline.join.config.WeChatProperties;
import cn.sduonline.join.mapper.AdminOrganizationMapper;
import cn.sduonline.join.mapper.DepartmentInterviewMapper;
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
    @Mock TransactionTemplate transactionTemplate;
    @Mock TransactionStatus transactionStatus;
    @Mock InterviewSseService interviewSseService;
    @Mock UserMapper userMapper;
    @Mock WeChatTemplateMessageService templateMessageService;
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
                organizationMapper, interviewMapper, transactionTemplate,
                interviewSseService, userMapper, templateMessageService,
                weChatProperties
        );
    }

    @Test
    void assignsFirstWaitingCandidate() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentInterview next = waitingCandidate();
        when(interviewMapper.selectNextWaitingForUpdate(12L)).thenReturn(next);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<DepartmentInterview>getArgument(0).setId(300L);
            return 1;
        }).when(interviewMapper).insertInterview(any());

        var result = service.callNext(12L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(300L, result.data().id());
        assertEquals(7, result.data().queueNumber());
        assertEquals("admin01", result.data().interviewerCasId());
        verify(interviewMapper).insertActive(next);
    }

    @Test
    void notifiesCalledCandidateUsingInterviewerNameAsWindow() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentInterview next = waitingCandidate();
        when(interviewMapper.selectNextWaitingForUpdate(12L)).thenReturn(next);
        User candidate = user(
                "20240001", "张三", "candidate-openid"
        );
        User interviewer = user("admin01", "李老师", null);
        when(userMapper.selectById("20240001")).thenReturn(candidate);
        when(userMapper.selectById("admin01")).thenReturn(interviewer);

        var result = service.callNext(12L, "admin01");

        assertTrue(result.isSuccess());
        verify(templateMessageService).send(
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
                        "thing9",
                        new cn.sduonline.join.client.WeChatApiClient.TemplateData(
                                "李老师"
                        )
                ))
        );
    }

    @Test
    void preventsInterviewerFromTakingSecondCandidate() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        DepartmentInterview active = waitingCandidate();
        active.setInterviewerCasId("admin01");
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(active);

        var result = service.callNext(12L, "admin01");

        assertEquals(BizCode.INTERVIEW_ADMIN_BUSY, result.error());
        verify(interviewMapper, never()).selectNextWaitingForUpdate(any());
    }

    @Test
    void reportsEmptyWaitingQueue() {
        when(organizationMapper.selectDepartmentById(12L))
                .thenReturn(new Department());
        when(interviewMapper.selectNextWaitingForUpdate(12L)).thenReturn(null);

        var result = service.callNext(12L, "admin01");

        assertEquals(BizCode.INTERVIEW_QUEUE_EMPTY, result.error());
    }

    @Test
    void finishingInterviewReleasesBothOccupants() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setInterviewerCasId("admin01");
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(active);

        var result = service.finish(
                12L, "admin01",
                new InterviewEvaluationRequest(5, " 表现优秀 ")
        );

        assertTrue(result.isSuccess());
        assertEquals(5, result.data().score());
        assertEquals("表现优秀", result.data().evaluation());
        verify(interviewMapper).finishInterview(
                org.mockito.ArgumentMatchers.eq(300L), any(),
                org.mockito.ArgumentMatchers.eq(5),
                org.mockito.ArgumentMatchers.eq("表现优秀")
        );
        verify(interviewMapper).deleteActive(300L);
    }

    @Test
    void supportsFinishingWithoutEvaluationForCompatibility() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setInterviewerCasId("admin01");
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(active);

        var result = service.finish(12L, "admin01", null);

        assertTrue(result.isSuccess());
        verify(interviewMapper).finishInterview(
                org.mockito.ArgumentMatchers.eq(300L), any(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
        );
    }

    @Test
    void updatesEvaluationAndReturnsSavedInterview() {
        DepartmentInterview saved = waitingCandidate();
        saved.setId(300L);
        saved.setScore(4);
        saved.setEvaluation("沟通清晰");
        when(interviewMapper.updateEvaluation(
                12L, 300L, 4, "沟通清晰"
        )).thenReturn(1);
        when(interviewMapper.selectByIdAndDepartment(12L, 300L))
                .thenReturn(saved);

        var result = service.updateEvaluation(
                12L, 300L,
                new InterviewEvaluationRequest(4, " 沟通清晰 ")
        );

        assertTrue(result.isSuccess());
        assertEquals(4, result.data().score());
        assertEquals("沟通清晰", result.data().evaluation());
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
        active.setInterviewerCasId("admin01");
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(active);
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

        var result = service.passCurrent(12L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(1, result.data().passCount());
        assertEquals(4L, target.getQueueOrder());
        verify(interviewMapper).deleteActive(300L);
        verify(interviewMapper).deleteInterview(300L);
        verify(interviewSseService).publish(12L);
    }

    @Test
    void rejectsPassAfterDepartmentLimitIsReached() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setInterviewerCasId("admin01");
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(active);
        when(interviewMapper.selectQueueConfig(12L))
                .thenReturn(new InterviewQueueConfigVO(3, 2));
        when(interviewMapper.selectCheckInForUpdate(200L))
                .thenReturn(checkIn(200L, 1L, 2));

        var result = service.passCurrent(12L, "admin01");

        assertEquals(BizCode.INTERVIEW_PASS_LIMIT_REACHED, result.error());
        verify(interviewMapper, never()).deleteActive(any());
    }

    @Test
    void stoppingCallingReleasesCandidateWithoutChangingQueueOrPassCount() {
        DepartmentInterview active = waitingCandidate();
        active.setId(300L);
        active.setInterviewerCasId("admin01");
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(active);
        InterviewQueueItemVO waiting = new InterviewQueueItemVO(
                200L, 100L, "20240001", "张三",
                7, 1L, 0, null, InterviewQueueStatus.WAITING,
                null, null, null, null, false
        );
        when(interviewMapper.selectCandidateQueueItem(12L, "20240001"))
                .thenReturn(waiting);

        var result = service.stopCalling(12L, "admin01");

        assertTrue(result.isSuccess());
        assertEquals(InterviewQueueStatus.WAITING, result.data().status());
        assertEquals(0, result.data().passCount());
        assertEquals(1L, result.data().queueOrder());
        verify(interviewMapper).deleteActive(300L);
        verify(interviewMapper).deleteInterview(300L);
        verify(interviewMapper, never()).updateCheckInQueue(any());
        verify(interviewSseService).publish(12L);
    }

    @Test
    void rejectsStoppingCallingWhenAdministratorHasNoActiveInterview() {
        when(interviewMapper.selectActiveByInterviewer("admin01"))
                .thenReturn(null);

        var result = service.stopCalling(12L, "admin01");

        assertEquals(BizCode.INTERVIEW_NOT_ACTIVE, result.error());
        verify(interviewMapper, never()).deleteActive(any());
        verify(interviewSseService, never()).publish(any());
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
        verify(interviewMapper).updateQueueConfig(12L, 3, 5);
    }

    @Test
    void rejectsEmptyQueueConfigPatch() {
        var result = service.patchQueueConfig(
                12L, new InterviewQueueConfigPatchRequest(null, null)
        );

        assertEquals(BizCode.PARAM_INVALID, result.error());
        verify(interviewMapper, never()).updateQueueConfig(
                any(), any(Integer.class), any(Integer.class)
        );
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
