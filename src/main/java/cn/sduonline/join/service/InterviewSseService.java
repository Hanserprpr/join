package cn.sduonline.join.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class InterviewSseService {

    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final Map<Long, List<Subscription>> subscriptions =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(
            Long departmentId,
            String eventName,
            Supplier<?> snapshotSupplier
    ) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        Subscription subscription =
                new Subscription(emitter, eventName, snapshotSupplier);
        subscriptions.computeIfAbsent(
                departmentId, ignored -> new CopyOnWriteArrayList<>()
        ).add(subscription);
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
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publishAfterCommit(Long departmentId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish(departmentId);
                        }
                    }
            );
        } else {
            publish(departmentId);
        }
    }

    public void publish(Long departmentId) {
        List<Subscription> departmentSubscriptions =
                subscriptions.get(departmentId);
        if (departmentSubscriptions == null) {
            return;
        }
        for (Subscription subscription : departmentSubscriptions) {
            sendSnapshot(departmentId, subscription);
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
                } catch (IOException exception) {
                    remove(departmentId, subscription);
                    subscription.emitter().complete();
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
            subscription.emitter().completeWithError(exception);
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
            Supplier<?> snapshotSupplier
    ) {
    }
}
