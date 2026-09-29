package io.github.libxposed.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time only marker copied from the libxposed {@code annotation} artifact. See
 * {@link SinceApi} for why a local copy exists.
 */
@Retention(RetentionPolicy.CLASS)
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.PACKAGE,
        ElementType.PARAMETER,
        ElementType.ANNOTATION_TYPE
})
public @interface InternalApi {
}
