package de.eitco.cicd.agentskills.core.skillcard;

/**
 * The {@code spec} section of a {@link SkillCard}.
 *
 * @param prompt the path of the prompt file, relative to the skill directory
 */
public record SkillSpec(String prompt) {
}
