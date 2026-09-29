package io.github.libxposed.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Compile-time only marker copied from the libxposed {@code annotation} artifact.
 *
 * <p>The reference API sources use this annotation, but the artifact is not part of the checksum
 * pinned API repository. Keeping a local copy lets {@code :libxposed-api} compile the API 102
 * sources offline. The annotation never ships in the module APK.</p>
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
public @interface SinceApi {
    int value();
}
