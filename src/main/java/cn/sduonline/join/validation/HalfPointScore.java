package cn.sduonline.join.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = HalfPointScoreValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HalfPointScore {

    String message() default "评分必须在 0.5 至 5 之间，且以 0.5 为步进";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
