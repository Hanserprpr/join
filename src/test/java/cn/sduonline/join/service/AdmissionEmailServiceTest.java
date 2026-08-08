package cn.sduonline.join.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import cn.sduonline.join.data.po.DepartmentApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AdmissionEmailServiceTest {

    @Mock JavaMailSender mailSender;

    @Test
    void putsApplicantNameOnFirstLine() {
        DepartmentApplication application = new DepartmentApplication();
        application.setApplicantName("张三");
        application.setEmail("zhangsan@example.com");
        AdmissionEmailService service = new AdmissionEmailService(
                mailSender, "join@example.com"
        );

        service.sendAfterCommit(
                List.of(application), " 录取通知 ", " 欢迎加入！ "
        );

        ArgumentCaptor<SimpleMailMessage> captor =
                ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("张三\n欢迎加入！", captor.getValue().getText());
        assertEquals("录取通知", captor.getValue().getSubject());
        assertEquals("join@example.com", captor.getValue().getFrom());
        assertEquals(
                "zhangsan@example.com",
                captor.getValue().getTo()[0]
        );
    }
}
