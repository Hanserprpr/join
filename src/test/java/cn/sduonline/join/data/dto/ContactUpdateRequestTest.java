package cn.sduonline.join.data.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 个人资料更新请求的字段校验。
 * <p>
 * 重点覆盖“空字符串表示清空”这一约定：选填字段必须放行空串，
 * 否则清空请求会在进入业务逻辑前被校验层挡下。
 */
class ContactUpdateRequestTest {

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
    void acceptsEmptyEmailAndQqAsClearRequest() {
        Set<ConstraintViolation<ContactUpdateRequest>> violations = validator.validate(
                new ContactUpdateRequest("", null, null, null, null, "")
        );

        assertTrue(violations.isEmpty(), () -> "unexpected violations: " + violations);
    }

    @Test
    void acceptsNullFields() {
        assertTrue(validator.validate(
                new ContactUpdateRequest(null, null, null, null, null, null)
        ).isEmpty());
    }

    @Test
    void acceptsValidEmail() {
        assertTrue(validator.validate(
                new ContactUpdateRequest(
                        "student@sdu.edu.cn", null, null, null, null, "123456")
        ).isEmpty());
    }

    @Test
    void rejectsMalformedEmail() {
        assertViolatesOnly("email", new ContactUpdateRequest(
                "not-an-email", null, null, null, null, null));
    }

    @Test
    void rejectsBlankEmail() {
        assertViolatesOnly("email", new ContactUpdateRequest(
                "   ", null, null, null, null, null));
    }

    @Test
    void rejectsBlankQq() {
        assertViolatesOnly("qq", new ContactUpdateRequest(
                null, null, null, null, null, "   "));
    }

    /**
     * 断言请求被拒绝，且所有违规都落在指定字段上。
     */
    private static void assertViolatesOnly(
            String property,
            ContactUpdateRequest request
    ) {
        Set<ConstraintViolation<ContactUpdateRequest>> violations =
                validator.validate(request);

        assertFalse(violations.isEmpty(), "expected the request to be rejected");
        assertEquals(
                Set.of(property),
                violations.stream()
                        .map(violation -> violation.getPropertyPath().toString())
                        .collect(Collectors.toSet())
        );
    }
}
