package io.oneiros.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert eine Klasse als Oneiros-Datenbank-Entität.
 * Beispiel: @OneirosEntity("users")
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneirosEntity {
    String value() default ""; // Der Name der Tabelle in SurrealDB
}