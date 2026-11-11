package de.eitco.cicd.agentskills.maven;

import java.lang.reflect.Field;

/**
 * Sets the private fields of a configuration object like maven does when injecting the plugin configuration.
 */
final class Configurations {

    private Configurations() {
    }

    static <T> T configure(T configuration, String field, Object value) {
        try {
            Field declared = configuration.getClass().getDeclaredField(field);
            declared.setAccessible(true);
            declared.set(configuration, value);
            return configuration;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
