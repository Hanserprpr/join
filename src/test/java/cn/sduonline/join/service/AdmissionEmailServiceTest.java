package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.sduonline.join.data.po.AdmissionEmailOutbox;
import cn.sduonline.join.data.po.DepartmentApplication;
import cn.sduonline.join.mapper.AdmissionEmailOutboxMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AdmissionEmailServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-08-09T07:00:00Z");

    @Mock AdmissionEmailOutboxMapper outboxMapper;
    @Mock JavaMailSender mailSender;
    @Mock ObjectProvider<JavaMailSender> mailSenderProvider;
    @Mock TransactionTemplate transactionTemplate;
    @Mock TransactionStatus transactionStatus;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZONE);
        org.mockito.Mockito.lenient()
                .when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    return callback.doInTransaction(transactionStatus);
                });
    }

    @Test
    void queuesPersonalizedMessageForReliableDelivery() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        AdmissionEmailService service = service(true);
        DepartmentApplication application = application();

        service.enqueue(
                List.of(application), " 录取通知 ", " 欢迎加入！ "
        );

        verify(outboxMapper).insert(
                100L,
                "zhangsan@example.com",
                "录取通知",
                "张三\n欢迎加入！",
                "PENDING",
                LocalDateTime.ofInstant(NOW, ZONE)
        );
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void recordsSkippedMessageWhenDeliveryIsDisabled() {
        AdmissionEmailService service = service(false);

        service.enqueue(
                List.of(application()), "录取通知", "欢迎加入！"
        );

        verify(outboxMapper).insert(
                100L,
                "zhangsan@example.com",
                "录取通知",
                "张三\n欢迎加入！",
                "SKIPPED",
                LocalDateTime.ofInstant(NOW, ZONE)
        );
        verify(mailSenderProvider, never()).getIfAvailable();
    }

    @Test
    void sendsClaimedMessageAndMarksItSent() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(outboxMapper.selectNextForUpdate(any(), any()))
                .thenReturn(outbox())
                .thenReturn(null);
        when(outboxMapper.markProcessing(7L, now())).thenReturn(1);
        when(outboxMapper.markSent(7L, now())).thenReturn(1);
        AdmissionEmailService service = service(true);

        service.dispatchPending();

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("join@example.com", captor.getValue().getFrom());
        assertEquals("zhangsan@example.com", captor.getValue().getTo()[0]);
        assertEquals("录取通知", captor.getValue().getSubject());
        assertEquals("张三\n欢迎加入！", captor.getValue().getText());
        verify(outboxMapper).markSent(7L, now());
    }

    @Test
    void schedulesRetryWhenDeliveryFails() {
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        when(outboxMapper.selectNextForUpdate(any(), any()))
                .thenReturn(outbox())
                .thenReturn(null);
        when(outboxMapper.markProcessing(7L, now())).thenReturn(1);
        when(outboxMapper.markRetry(
                7L, now().plusSeconds(30), "smtp down"
        )).thenReturn(1);
        doThrow(new MailSendException("smtp down"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        AdmissionEmailService service = service(true);

        service.dispatchPending();

        verify(outboxMapper).markRetry(
                7L, now().plusSeconds(30), "smtp down"
        );
        verify(outboxMapper, never()).markSent(any(), any());
    }

    @Test
    void rejectsEnabledDeliveryWithoutMailConfiguration() {
        AdmissionEmailService service = new AdmissionEmailService(
                outboxMapper, mailSenderProvider, transactionTemplate,
                true, "", "", clock
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.enqueue(
                        List.of(application()), "录取通知", "欢迎加入！"
                )
        );
        verify(outboxMapper, never()).insert(
                any(), any(), any(), any(), any(), any()
        );
    }

    private AdmissionEmailService service(boolean enabled) {
        return new AdmissionEmailService(
                outboxMapper, mailSenderProvider, transactionTemplate,
                enabled, "smtp.example.com", "join@example.com", clock
        );
    }

    private static DepartmentApplication application() {
        DepartmentApplication application = new DepartmentApplication();
        application.setId(100L);
        application.setApplicantName("张三");
        application.setEmail("zhangsan@example.com");
        return application;
    }

    private static AdmissionEmailOutbox outbox() {
        AdmissionEmailOutbox outbox = new AdmissionEmailOutbox();
        outbox.setId(7L);
        outbox.setRecipient("zhangsan@example.com");
        outbox.setSubject("录取通知");
        outbox.setContent("张三\n欢迎加入！");
        outbox.setAttempts(0);
        return outbox;
    }

    private static LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZONE);
    }
}
