package io.github.mbroughani81.perfspec;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for a guard method.
 * When the trace generator encounters an if‑statement whose
 * condition directly invokes an {@code @Skip}‑annotated method,
 * it will NOT explore the true branch.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Skip {
}
