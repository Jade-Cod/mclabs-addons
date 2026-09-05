package dev.jade.labsaddons.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks which {@link ConfigSection} a {@link LabsAddonsConfig} field is written to.
 *
 * <p>An unannotated field defaults to {@link ConfigSection#SETTINGS} — deliberately the
 * safe default, since that file is always written and always small. Forgetting the
 * annotation therefore misfiles a field rather than losing it or bloating
 * {@code runners.json}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Section {
	ConfigSection value();
}
