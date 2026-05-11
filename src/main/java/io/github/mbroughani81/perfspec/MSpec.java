package io.github.mbroughani81.perfspec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MSpec {
    public enum SpecMode {
        LATENCY, // exact max must be respected
        SQUEEZE // no fixed max; squeeze out any avoidable latency
    }

    SpecMode mode() default SpecMode.LATENCY; // NEW

    long max() default Long.MAX_VALUE;

    String unit() default "MILLISECONDS";

    boolean sink() default false;

    int percentile() default 100;

    String desc() default "";
}
