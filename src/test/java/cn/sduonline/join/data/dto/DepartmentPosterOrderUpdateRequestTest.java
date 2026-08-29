package cn.sduonline.join.data.dto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DepartmentPosterOrderUpdateRequestTest {

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
    void acceptsEmptyAndValidCompleteOrders() {
        assertTrue(validator.validate(
                new DepartmentPosterOrderUpdateRequest(List.of())
        ).isEmpty());
        assertTrue(validator.validate(new DepartmentPosterOrderUpdateRequest(
                List.of(
                        new DepartmentPosterOrderItemRequest(2L, 0),
                        new DepartmentPosterOrderItemRequest(1L, 1)
                )
        )).isEmpty());
    }

    @Test
    void rejectsMissingOversizedOrNullPosterLists() {
        assertFalse(validator.validate(
                new DepartmentPosterOrderUpdateRequest(null)
        ).isEmpty());

        List<DepartmentPosterOrderItemRequest> oversized = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            oversized.add(new DepartmentPosterOrderItemRequest(
                    (long) index + 1, index
            ));
        }
        assertFalse(validator.validate(
                new DepartmentPosterOrderUpdateRequest(oversized)
        ).isEmpty());

        assertFalse(validator.validate(new DepartmentPosterOrderUpdateRequest(
                java.util.Collections.singletonList(null)
        )).isEmpty());
    }

    @Test
    void rejectsInvalidPosterIdsAndSortOrders() {
        assertFalse(validator.validate(new DepartmentPosterOrderUpdateRequest(
                List.of(new DepartmentPosterOrderItemRequest(0L, 0))
        )).isEmpty());
        assertFalse(validator.validate(new DepartmentPosterOrderUpdateRequest(
                List.of(new DepartmentPosterOrderItemRequest(1L, -1))
        )).isEmpty());
        assertFalse(validator.validate(new DepartmentPosterOrderUpdateRequest(
                List.of(new DepartmentPosterOrderItemRequest(null, 0))
        )).isEmpty());
        assertFalse(validator.validate(new DepartmentPosterOrderUpdateRequest(
                List.of(new DepartmentPosterOrderItemRequest(1L, null))
        )).isEmpty());
    }
}
