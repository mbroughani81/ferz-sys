package io.github.mbroughani81.perfspec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.CONSTRUCTOR })
public @interface MSpec {
    long max();

    TimeUnit unit() default TimeUnit.MILLISECONDS;

    boolean sink() default false;

    int percentile() default 100;

    String desc() default "";
}
