package cn.sduonline.join.data.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DepartmentPosterRequestTest {

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
    void acceptsEitherExistingIdOrNewUrl() {
        assertTrue(validator.validate(
                new DepartmentPosterRequest(1L, null, null)
        ).isEmpty());
        assertTrue(validator.validate(new DepartmentPosterRequest(
                null, "https://files.example.com/join/posters/a.png", 0
        )).isEmpty());
    }

    @Test
    void rejectsMissingAndAmbiguousReferences() {
        assertFalse(validator.validate(
                new DepartmentPosterRequest(null, null, 0)
        ).isEmpty());
        assertFalse(validator.validate(
                new DepartmentPosterRequest(null, "   ", 0)
        ).isEmpty());
        assertFalse(validator.validate(new DepartmentPosterRequest(
                1L, "https://files.example.com/join/posters/a.png", 0
        )).isEmpty());
    }
}
