package io.oneiros.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OneirosEncrypted {
    // Markiert Felder, die vor dem Speichern verschlüsselt werden müssen.
}