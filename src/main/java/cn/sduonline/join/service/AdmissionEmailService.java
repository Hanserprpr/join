package cn.sduonline.join.service;

import cn.sduonline.join.data.po.AdmissionEmailOutbox;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.mapper.AdmissionEmailOutboxMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class AdmissionEmailService {

    private static final int DISPATCH_BATCH_SIZE = 20;
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration MAX_RETRY_DELAY = Duration.ofHours(1);
    private static final DateTimeFormatter EMAIL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
                    .withZone(ZoneId.of("Asia/Shanghai"));

    private final AdmissionEmailOutboxMapper outboxMapper;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final TransactionTemplate transactionTemplate;
    private final boolean enabled;
    private final String host;
    private final String sender;
    private final Clock clock;

    @Autowired
    public AdmissionEmailService(
            AdmissionEmailOutboxMapper outboxMapper,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            TransactionTemplate transactionTemplate,
            @Value("${app.admission-email.enabled:false}") boolean enabled,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String sender
    ) {
        this(
                outboxMapper, mailSenderProvider, transactionTemplate,
                enabled, host, sender, Clock.systemDefaultZone()
        );
    }

    AdmissionEmailService(
            AdmissionEmailOutboxMapper outboxMapper,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            TransactionTemplate transactionTemplate,
            boolean enabled,
            String host,
            String sender,
            Clock clock
    ) {
        this.outboxMapper = outboxMapper;
        this.mailSenderProvider = mailSenderProvider;
        this.transactionTemplate = transactionTemplate;
        this.enabled = enabled;
        this.host = host;
        this.sender = sender;
        this.clock = clock;
    }

    public void enqueue(
            List<DepartmentApplication> applications,
            String subject,
            String content
    ) {
        if (enabled) {
            requireConfiguredMailSender();
        }
        Instant currentInstant = clock.instant();
        LocalDateTime now = LocalDateTime.ofInstant(currentInstant, clock.getZone());
        String datedContent = content.trim()
                .replace("{time}", EMAIL_DATE_FORMAT.format(currentInstant));
        String status = enabled ? "PENDING" : "SKIPPED";
        for (DepartmentApplication application : applications) {
            outboxMapper.insert(
                    application.getId(),
                    application.getEmail(),
                    subject.trim(),
                    datedContent.replace("{name}", application.getApplicantName()),
                    status,
                    now
            );
        }
    }

    @Scheduled(fixedDelayString = "${app.admission-email.poll-delay-ms:5000}")
    public void dispatchPending() {
        if (!enabled) {
            return;
        }
        JavaMailSender mailSender;
        try {
            mailSender = requireConfiguredMailSender();
        } catch (IllegalStateException exception) {
            log.error("Admission email delivery is enabled but not configured");
            return;
        }
        for (int index = 0; index < DISPATCH_BATCH_SIZE; index++) {
            AdmissionEmailOutbox message = claimNext();
            if (message == null) {
                return;
            }
            deliver(mailSender, message);
        }
    }

    private AdmissionEmailOutbox claimNext() {
        return transactionTemplate.execute(status -> {
            LocalDateTime now = LocalDateTime.now(clock);
            AdmissionEmailOutbox message = outboxMapper.selectNextForUpdate(
                    now, now.minus(PROCESSING_TIMEOUT)
            );
            if (message == null) {
                return null;
            }
            if (outboxMapper.markProcessing(message.getId(), now) != 1) {
                throw new IllegalStateException(
                        "Failed to claim admission email " + message.getId()
                );
            }
            return message;
        });
    }

    private void deliver(
            JavaMailSender mailSender,
            AdmissionEmailOutbox message
    ) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(sender);
            mail.setTo(message.getRecipient());
            mail.setSubject(message.getSubject());
            mail.setText(message.getContent());
            mailSender.send(mail);
            if (outboxMapper.markSent(
                    message.getId(), LocalDateTime.now(clock)
            ) != 1) {
                throw new IllegalStateException(
                        "Failed to mark admission email sent " + message.getId()
                );
            }
        } catch (RuntimeException exception) {
            int attempts = message.getAttempts() == null
                    ? 1 : message.getAttempts() + 1;
            LocalDateTime retryAt = LocalDateTime.now(clock)
                    .plus(retryDelay(attempts));
            if (outboxMapper.markRetry(
                    message.getId(), retryAt, abbreviate(exception)
            ) != 1) {
                throw new IllegalStateException(
                        "Failed to schedule admission email retry "
                                + message.getId(),
                        exception
                );
            }
            log.warn(
                    "Admission email delivery failed; messageId={}, retryAt={}",
                    message.getId(), retryAt, exception
            );
        }
    }

    private JavaMailSender requireConfiguredMailSender() {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (!StringUtils.hasText(host)
                || !StringUtils.hasText(sender)
                || mailSender == null) {
            throw new IllegalStateException(
                    "Admission email delivery is enabled but mail is not configured"
            );
        }
        return mailSender;
    }

    private static Duration retryDelay(int attempts) {
        int exponent = Math.min(Math.max(attempts - 1, 0), 7);
        Duration delay = Duration.ofSeconds(30L << exponent);
        return delay.compareTo(MAX_RETRY_DELAY) > 0
                ? MAX_RETRY_DELAY : delay;
    }

    private static String abbreviate(RuntimeException exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            message = exception.getClass().getSimpleName();
        }
        return message.length() <= 1000
                ? message : message.substring(0, 1000);
    }
}
