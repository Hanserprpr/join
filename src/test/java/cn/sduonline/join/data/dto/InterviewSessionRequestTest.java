package cn.sduonline.join.data.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InterviewSessionRequestTest {

    @Test
    void rejectsBlankSessionName() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 9, 1, 9, 0);
        var request = new InterviewSessionRequest(
                "  ", startsAt, startsAt.plusHours(3), "中心校区 101",
                50, false, 8
        );
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).stream()
                    .anyMatch(violation -> violation.getPropertyPath()
                            .toString().equals("name")));
        }
    }
}
