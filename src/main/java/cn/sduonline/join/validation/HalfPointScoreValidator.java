package cn.sduonline.join.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;

public class HalfPointScoreValidator
        implements ConstraintValidator<HalfPointScore, BigDecimal> {

    private static final BigDecimal MIN = new BigDecimal("0.5");
    private static final BigDecimal MAX = new BigDecimal("5.0");
    private static final BigDecimal STEP = new BigDecimal("0.5");

    @Override
    public boolean isValid(
            BigDecimal value,
            ConstraintValidatorContext context
    ) {
        if (value == null) {
            return true;
        }
        return value.compareTo(MIN) >= 0
                && value.compareTo(MAX) <= 0
                && value.remainder(STEP).compareTo(BigDecimal.ZERO) == 0;
    }
}
