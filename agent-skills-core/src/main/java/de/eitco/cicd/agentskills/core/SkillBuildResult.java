package de.eitco.cicd.agentskills.core;

import de.eitco.cicd.agentskills.core.oci.Digest;
import de.eitco.cicd.agentskills.core.oci.OciDescriptor;
import de.eitco.cicd.agentskills.core.oci.SkillStatus;
import de.eitco.cicd.agentskills.core.skillcard.SkillCard;

import java.nio.file.Path;

/**
 * The result of {@link SkillImageBuilder}.
 */
public record SkillBuildResult(Path outputDirectory, SkillCard skillCard, SkillStatus status, String tag,
                               OciDescriptor manifest, OciDescriptor config, OciDescriptor layer, Digest diffId) {
}
