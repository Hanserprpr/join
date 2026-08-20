package cn.sduonline.join.service;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.vo.Result;
import cn.sduonline.join.security.scope.OrgType;
import cn.sduonline.join.security.scope.PermissionCode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSseService {

    private final AuthorizationService authorizationService;

    /** 候选人个人排队状态事件名。 */
    private static final String CANDIDATE_EVENT = "my-queue-status-updated";
    /** 每部门队列订阅（面试官查看队列）上限。 */
    private static final int MAX_QUEUE_SUBSCRIPTIONS_PER_DEPARTMENT = 50;
    /** 每部门候选人个人状态订阅上限（排队的候选人数量可能较多）。 */
    private static final int MAX_CANDIDATE_SUBSCRIPTIONS_PER_DEPARTMENT = 1000;
    /** 每部门面试室状态订阅上限（多个面试室并发处理同一队列）。 */
    private static final int MAX_ROOM_SUBSCRIPTIONS_PER_DEPARTMENT = 200;
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    /** 权限复查周期；比心跳稀疏，避免长连接把权限查询放大成持续负载。 */
    private static final long AUTHORIZATION_RECHECK_MILLIS = 60_000L;
    private final Map<Long, List<Subscription>> subscriptions =
            new ConcurrentHashMap<>();

    /**
     * 订阅部门级事件（队列、个人排队状态），随队列变化广播。
     * 用于候选人查看自己的排队状态，数据本身就属于订阅者，无需复查权限。
     *
     * @param departmentId 部门 ID
     * @param eventName 事件名
     * @param snapshotSupplier 快照函数
     * @return SSE 连接
     */
    public SseEmitter subscribe(
            Long departmentId,
            String eventName,
            Supplier<?> snapshotSupplier
    ) {
        return subscribeInternal(
                departmentId, null, eventName, snapshotSupplier, null
        );
    }

    /**
     * 订阅部门级事件，并周期性复查订阅者权限。
     *
     * @param departmentId 部门 ID
     * @param eventName 事件名
     * @param snapshotSupplier 快照函数
     * @param casId 订阅者学号
     * @param permission 订阅期间必须持续持有的权限
     * @return SSE 连接
     */
    public SseEmitter subscribe(
            Long departmentId,
            String eventName,
            Supplier<?> snapshotSupplier,
            String casId,
            PermissionCode permission
    ) {
        return subscribeInternal(
                departmentId, null, eventName, snapshotSupplier,
                new AccessCheck(casId, permission.code(), departmentId)
        );
    }

    /**
     * 订阅指定面试室事件，仅在面试室状态变化时推送，并周期性复查权限。
     *
     * @param departmentId 部门 ID
     * @param roomId 面试室 ID
     * @param eventName 事件名
     * @param snapshotSupplier 快照函数
     * @param casId 订阅者学号
     * @param permission 订阅期间必须持续持有的权限
     * @return SSE 连接
     */
    public SseEmitter subscribeRoom(
            Long departmentId,
            Long roomId,
            String eventName,
            Supplier<?> snapshotSupplier,
            String casId,
            PermissionCode permission
    ) {
        return subscribeInternal(
                departmentId, roomId, eventName, snapshotSupplier,
                new AccessCheck(casId, permission.code(), departmentId)
        );
    }

    private SseEmitter subscribeInternal(
            Long departmentId,
            Long roomId,
            String eventName,
            Supplier<?> snapshotSupplier,
            AccessCheck access
    ) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Subscription subscription = new Subscription(
                emitter, eventName, snapshotSupplier, roomId, access
        );
        List<Subscription> departmentSubscriptions = subscriptions.computeIfAbsent(
                departmentId, ignored -> new CopyOnWriteArrayList<>()
        );
        if (countByType(departmentId, roomId, eventName)
                >= maxSubscriptions(roomId, eventName)) {
            return failed(Result.fail(BizCode.TOO_MANY_REQUESTS));
        }
        departmentSubscriptions.add(subscription);
        emitter.onCompletion(() -> remove(departmentId, subscription));
        emitter.onTimeout(() -> remove(departmentId, subscription));
        emitter.onError(error -> remove(departmentId, subscription));
        sendSnapshot(departmentId, subscription);
        return emitter;
    }

    public SseEmitter failed(Object payload) {
        SseEmitter emitter = new SseEmitter(5_000L);
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("business-error")
                            .data(payload)
            );
            emitter.complete();
        } catch (Exception exception) {
            quietlyComplete(emitter);
        }
        return emitter;
    }

    /**
     * 队列变化后广播部门级订阅（队列、个人排队状态）。
     *
     * @param departmentId 部门 ID
     */
    public void publishQueueAfterCommit(Long departmentId) {
        afterCommit(departmentId, () -> publishQueue(departmentId));
    }

    /**
     * 面试室状态变化后推送该面试室订阅。
     *
     * @param departmentId 部门 ID
     * @param roomId 面试室 ID
     */
    public void publishRoomAfterCommit(Long departmentId, Long roomId) {
        afterCommit(departmentId, () -> publishRoom(departmentId, roomId));
    }

    private void afterCommit(Long departmentId, Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            action.run();
        }
    }

    /**
     * 广播部门级订阅（队列、个人排队状态等非面试室订阅）。
     *
     * @param departmentId 部门 ID
     */
    public void publishQueue(Long departmentId) {
        publishIf(departmentId, subscription -> subscription.roomId() == null);
    }

    /**
     * 推送指定面试室的订阅。
     *
     * @param departmentId 部门 ID
     * @param roomId 面试室 ID
     */
    public void publishRoom(Long departmentId, Long roomId) {
        publishIf(
                departmentId,
                subscription -> roomId.equals(subscription.roomId())
        );
    }

    private static int maxSubscriptions(Long roomId, String eventName) {
        if (roomId != null) {
            return MAX_ROOM_SUBSCRIPTIONS_PER_DEPARTMENT;
        }
        return CANDIDATE_EVENT.equals(eventName)
                ? MAX_CANDIDATE_SUBSCRIPTIONS_PER_DEPARTMENT
                : MAX_QUEUE_SUBSCRIPTIONS_PER_DEPARTMENT;
    }

    private long countByType(
            Long departmentId, Long roomId, String eventName
    ) {
        List<Subscription> departmentSubscriptions =
                subscriptions.get(departmentId);
        if (departmentSubscriptions == null) {
            return 0;
        }
        long count = 0;
        for (Subscription subscription : departmentSubscriptions) {
            if (matchesType(subscription, roomId, eventName)) {
                count++;
            }
        }
        return count;
    }

    private static boolean matchesType(
            Subscription subscription, Long roomId, String eventName
    ) {
        if (roomId != null) {
            return roomId.equals(subscription.roomId());
        }
        boolean isCandidate = CANDIDATE_EVENT.equals(subscription.eventName());
        return subscription.roomId() == null
                && isCandidate == CANDIDATE_EVENT.equals(eventName);
    }

    private void publishIf(
            Long departmentId,
            Predicate<Subscription> filter
    ) {
        List<Subscription> departmentSubscriptions =
                subscriptions.get(departmentId);
        if (departmentSubscriptions == null) {
            return;
        }
        for (Subscription subscription : departmentSubscriptions) {
            if (filter.test(subscription)) {
                sendSnapshot(departmentId, subscription);
            }
        }
    }

    /**
     * 心跳。只负责保活，权限复查交给
     * {@link #revokeUnauthorizedSubscriptions()}，避免每 20 秒把权限查询
     * 按订阅数放大成持续的数据库负载。
     */
    @Scheduled(fixedRate = 20_000)
    public void heartbeat() {
        subscriptions.forEach((departmentId, departmentSubscriptions) -> {
            for (Subscription subscription : departmentSubscriptions) {
                try {
                    subscription.emitter().send(
                            SseEmitter.event()
                                    .name("heartbeat")
                                    .data(Instant.now().toString())
                    );
                } catch (Exception exception) {
                    remove(departmentId, subscription);
                    quietlyComplete(subscription.emitter());
                }
            }
        });
    }

    /**
     * 周期性复查订阅者权限，让角色撤销最多 60 秒内断开对应的长连接。
     * <p>
     * SSE 建立连接时的 {@code @DepartmentPermission} 只校验一次，之后不再经过
     * 拦截器和切面，因此必须在这里补一次复查。同一个 {@link AccessCheck}
     * 在一轮里只查一次数据库：一位面试官通常同时订阅队列和多个面试室，
     * 去重后查询次数按"人数 × 部门"而不是"连接数"增长。
     * <p>
     * 只能复查权限，复查不了登录态：连接建立后不再持有 Token，登录态失效要靠
     * {@link #TIMEOUT_MILLIS} 到期后前端重连时重新走完整鉴权。
     */
    @Scheduled(fixedRate = AUTHORIZATION_RECHECK_MILLIS)
    public void revokeUnauthorizedSubscriptions() {
        Map<AccessCheck, Boolean> decided = new HashMap<>();
        subscriptions.forEach((departmentId, departmentSubscriptions) -> {
            for (Subscription subscription : departmentSubscriptions) {
                AccessCheck access = subscription.access();
                if (access == null) {
                    continue;
                }
                if (decided.computeIfAbsent(access, this::stillAuthorized)) {
                    continue;
                }
                revoke(departmentId, subscription);
            }
        });
    }

    /**
     * 复查一条授权。复查本身失败（例如数据库抖动）按失去权限处理：断开后前端
     * 重连会重新走一遍完整鉴权，比继续推送陈旧数据安全。
     */
    private boolean stillAuthorized(AccessCheck access) {
        try {
            return authorizationService.canAccessWithPermission(
                    access.casId(),
                    access.permission(),
                    OrgType.DEPARTMENT,
                    access.departmentId()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "SSE authorization re-check failed, casId={}, departmentId={}",
                    access.casId(),
                    access.departmentId(),
                    exception
            );
            return false;
        }
    }

    /** 下发无权限事件并断开该订阅。 */
    private void revoke(Long departmentId, Subscription subscription) {
        remove(departmentId, subscription);
        try {
            subscription.emitter().send(
                    SseEmitter.event()
                            .name("business-error")
                            .data(Result.fail(BizCode.NO_PERMISSION))
            );
        } catch (Exception ignored) {
            // 连接可能已经断开，直接进入 complete。
        }
        quietlyComplete(subscription.emitter());
    }

    private void sendSnapshot(
            Long departmentId,
            Subscription subscription
    ) {
        try {
            subscription.emitter().send(
                    SseEmitter.event()
                            .name(subscription.eventName())
                            .data(subscription.snapshotSupplier().get())
            );
        } catch (Exception exception) {
            remove(departmentId, subscription);
            quietlyComplete(subscription.emitter());
        }
    }

    private static void quietlyComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private void remove(Long departmentId, Subscription subscription) {
        List<Subscription> departmentSubscriptions =
                subscriptions.get(departmentId);
        if (departmentSubscriptions == null) {
            return;
        }
        departmentSubscriptions.remove(subscription);
        if (departmentSubscriptions.isEmpty()) {
            subscriptions.remove(departmentId, departmentSubscriptions);
        }
    }

    private record Subscription(
            SseEmitter emitter,
            String eventName,
            Supplier<?> snapshotSupplier,
            Long roomId,
            AccessCheck access
    ) {
    }

    /**
     * 一条订阅需要持续满足的授权条件，同时用作复查去重的键。
     */
    private record AccessCheck(
            String casId,
            String permission,
            Long departmentId
    ) {
    }
}
