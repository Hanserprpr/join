package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import java.util.concurrent.atomic.AtomicInteger;
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
                12L, "queue-updated", () -> "snapshot",
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
        service.subscribe(12L, "my-queue-status-updated", () -> "snapshot");

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
                12L, "queue-updated", snapshots::incrementAndGet,
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        int afterSubscribe = snapshots.get();

        service.revokeUnauthorizedSubscriptions();
        service.publishQueue(12L);

        assertEquals(afterSubscribe, snapshots.get());
    }

    @Test
    void recheckFailureDisconnectsInsteadOfKeepingTheConnection() {
        when(authorizationService.canAccessWithPermission(
                anyString(), anyString(), any(OrgType.class), anyLong()
        )).thenThrow(new IllegalStateException("database is down"));
        AtomicInteger snapshots = new AtomicInteger();

        service.subscribe(
                12L, "queue-updated", snapshots::incrementAndGet,
                "20240001", PermissionCode.INTERVIEW_EVALUATE
        );
        int afterSubscribe = snapshots.get();

        service.revokeUnauthorizedSubscriptions();
        service.publishQueue(12L);

        assertEquals(afterSubscribe, snapshots.get());
    }
}
