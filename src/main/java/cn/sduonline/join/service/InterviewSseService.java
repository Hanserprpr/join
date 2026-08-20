package cn.sduonline.join.service;

import cn.sduonline.join.data.enums.BizCode;
import cn.sduonline.join.data.vo.Result;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class InterviewSseService {

    /** 候选人个人排队状态事件名。 */
    private static final String CANDIDATE_EVENT = "my-queue-status-updated";
    /** 每部门队列订阅（面试官查看队列）上限。 */
    private static final int MAX_QUEUE_SUBSCRIPTIONS_PER_DEPARTMENT = 50;
    /** 每部门候选人个人状态订阅上限（排队的候选人数量可能较多）。 */
    private static final int MAX_CANDIDATE_SUBSCRIPTIONS_PER_DEPARTMENT = 1000;
    /** 每部门面试室状态订阅上限（多个面试室并发处理同一队列）。 */
    private static final int MAX_ROOM_SUBSCRIPTIONS_PER_DEPARTMENT = 200;
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final Map<Long, List<Subscription>> subscriptions =
            new ConcurrentHashMap<>();

    /**
     * 订阅部门级事件（队列、个人排队状态），随队列变化广播。
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
        return subscribeInternal(departmentId, null, eventName, snapshotSupplier);
    }

    /**
     * 订阅指定面试室事件，仅在面试室状态变化时推送。
     *
     * @param departmentId 部门 ID
     * @param roomId 面试室 ID
     * @param eventName 事件名
     * @param snapshotSupplier 快照函数
     * @return SSE 连接
     */
    public SseEmitter subscribeRoom(
            Long departmentId,
            Long roomId,
            String eventName,
            Supplier<?> snapshotSupplier
    ) {
        return subscribeInternal(departmentId, roomId, eventName, snapshotSupplier);
    }

    private SseEmitter subscribeInternal(
            Long departmentId,
            Long roomId,
            String eventName,
            Supplier<?> snapshotSupplier
    ) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Subscription subscription =
                new Subscription(emitter, eventName, snapshotSupplier, roomId);
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
            Long roomId
    ) {
    }
}
