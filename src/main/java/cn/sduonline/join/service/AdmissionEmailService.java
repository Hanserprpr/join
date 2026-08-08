package cn.sduonline.join.service;

import cn.sduonline.join.data.po.DepartmentApplication;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AdmissionEmailService {

    private final JavaMailSender mailSender;
    private final String sender;

    public AdmissionEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String sender
    ) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    public void sendAfterCommit(
            List<DepartmentApplication> applications,
            String subject,
            String content
    ) {
        Runnable task = () -> send(applications, subject, content);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        task.run();
                    }
                }
        );
    }

    private void send(
            List<DepartmentApplication> applications,
            String subject,
            String content
    ) {
        for (DepartmentApplication application : applications) {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(application.getEmail());
            message.setSubject(subject.trim());
            message.setText(application.getApplicantName() + "\n" + content.trim());
            mailSender.send(message);
        }
    }
}
