package de.eitco.cicd.agentskills.core.skillcard;

/**
 * The {@code metadata} section of a {@link SkillCard}.
 */
public record SkillMetadata(String name, String namespace, String version, String description) {
}
