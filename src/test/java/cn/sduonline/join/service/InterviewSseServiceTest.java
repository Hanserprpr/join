package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.dto.MyInterviewQueueStatusVO;
import cn.sduonline.join.data.enums.InterviewQueueStatus;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewSseServiceTest {

    @Mock
    private AuthorizationService authorizationService;

    private InterviewSseService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSseService(authorizationService);
    }

    @Test
    void oneAuthorizationQueryCoversEveryConnectionOfTheSameSubscriber() {
        when(authorizationService.canAccessWithPermission(
                "20240001",
                PermissionCode.INTERVIEW_EVALUATE.code(),
                OrgType.DEPARTMENT,
                12L
        )).thenReturn(true);

        // 一位面试官通常同时开着队列订阅和两个面试室订阅。
        service.subscribe(
                12L, 5L, "queue-updated", () -> "snapshot",
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        service.subscribeRoom(
                12L, 1L, "interview-room-state-updated", () -> "snapshot",
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        service.subscribeRoom(
                12L, 2L, "interview-room-state-updated", () -> "snapshot",
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );

        service.revokeUnauthorizedSubscriptions();

        // 三条连接同属一个 (学号, 权限, 部门)，一轮里只应该查一次。
        verify(authorizationService, times(1)).canAccessWithPermission(
                anyString(), anyString(), any(OrgType.class), anyLong()
        );
    }

    @Test
    void candidateSubscriptionIsNeverRecheckedAgainstPermissions() {
        service.subscribeSelfScoped(
                12L, "my-queue-status-updated", () -> "snapshot"
        );

        service.revokeUnauthorizedSubscriptions();

        verify(authorizationService, times(0)).canAccessWithPermission(
                anyString(), anyString(), any(OrgType.class), anyLong()
        );
    }

    @Test
    void revokedPermissionStopsFurtherPushes() {
        when(authorizationService.canAccessWithPermission(
                "20240001",
                PermissionCode.INTERVIEW_EVALUATE.code(),
                OrgType.DEPARTMENT,
                12L
        )).thenReturn(false);
        AtomicInteger snapshots = new AtomicInteger();

        service.subscribe(
                12L, 5L, "queue-updated", snapshots::incrementAndGet,
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        int afterSubscribe = snapshots.get();

        service.revokeUnauthorizedSubscriptions();
        service.publishQueue(12L, 5L);

        assertEquals(afterSubscribe, snapshots.get());
    }

    @Test
    void queueSubscriptionOnlyReceivesItsOwnSessionPushes() {
        AtomicInteger campusA = new AtomicInteger();
        AtomicInteger campusB = new AtomicInteger();

        service.subscribe(
                12L, 5L, "queue-updated", campusA::incrementAndGet,
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        service.subscribe(
                12L, 6L, "queue-updated", campusB::incrementAndGet,
                "20240002", PermissionCode.INTERVIEW_EVALUATE
        );
        int afterSubscribeA = campusA.get();
        int afterSubscribeB = campusB.get();

        service.publishQueue(12L, 5L);

        assertEquals(afterSubscribeA + 1, campusA.get());
        assertEquals(afterSubscribeB, campusB.get());
    }

    @Test
    void candidateSubscriptionConvergesOnTheSessionItsSnapshotReports() {
        AtomicInteger pushes = new AtomicInteger();
        AtomicReference<MyInterviewQueueStatusVO> snapshot =
                new AtomicReference<>(status(5L));

        service.subscribeSelfScoped(
                12L, "my-queue-status-updated",
                () -> {
                    pushes.incrementAndGet();
                    return snapshot.get();
                }
        );
        int afterSubscribe = pushes.get();

        // 快照自报场次 5，另一个场次的推送不应该唤醒这条订阅。
        service.publishQueue(12L, 6L);
        assertEquals(afterSubscribe, pushes.get());

        service.publishQueue(12L, 5L);
        assertEquals(afterSubscribe + 1, pushes.get());

        // 取消签到后快照为空，订阅回落为部门级，
        // 换场次签到的推送仍能到达并重新收敛。
        snapshot.set(null);
        service.publishQueue(12L, 5L);
        snapshot.set(status(6L));
        service.publishQueue(12L, 6L);
        int afterMove = pushes.get();

        service.publishQueue(12L, 5L);
        assertEquals(afterMove, pushes.get());
        service.publishQueue(12L, 6L);
        assertEquals(afterMove + 1, pushes.get());
    }

    private static MyInterviewQueueStatusVO status(Long sessionId) {
        return new MyInterviewQueueStatusVO(
                12L, sessionId, 7, InterviewQueueStatus.WAITING,
                0, List.of(), List.of()
        );
    }

    @Test
    void recheckFailureDisconnectsInsteadOfKeepingTheConnection() {
        when(authorizationService.canAccessWithPermission(
                anyString(), anyString(), any(OrgType.class), anyLong()
        )).thenThrow(new IllegalStateException("database is down"));
        AtomicInteger snapshots = new AtomicInteger();

        service.subscribe(
                12L, 5L, "queue-updated", snapshots::incrementAndGet,
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        int afterSubscribe = snapshots.get();

        service.revokeUnauthorizedSubscriptions();
        service.publishQueue(12L, 5L);

        assertEquals(afterSubscribe, snapshots.get());
    }
}
