package cn.sduonline.join.data.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterviewEvaluationRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void acceptsWholeAndHalfPointScores() {
        assertValid("0.5");
        assertValid("1");
        assertValid("3.5");
        assertValid("5.0");
    }

    @Test
    void rejectsScoresOutsideRangeOrNotOnHalfPointStep() {
        assertInvalid("0");
        assertInvalid("0.4");
        assertInvalid("3.2");
        assertInvalid("5.5");
    }

    private static void assertValid(String score) {
        assertTrue(validator.validate(request(score)).isEmpty());
    }

    private static void assertInvalid(String score) {
        assertFalse(validator.validate(request(score)).isEmpty());
    }

    private static InterviewEvaluationRequest request(String score) {
        return new InterviewEvaluationRequest(new BigDecimal(score), null);
    }
}
