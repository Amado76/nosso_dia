package com.nossodia.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, RECORD_COMPONENT, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@NotNull
@Size(min = 8, max = 128)
@Pattern(regexp = "(?s)(?=.*\\p{Lu})(?=.*[0-9])(?=.*[\\p{P}\\p{S}]).*")
@ReportAsSingleViolation
public @interface ValidPassword {
    String message() default "{validation.auth.password}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
