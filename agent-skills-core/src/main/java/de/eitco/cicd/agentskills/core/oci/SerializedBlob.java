package de.eitco.cicd.agentskills.core.oci;

/**
 * Serialized JSON content together with its descriptor.
 *
 * @param bytes      the exact bytes that were hashed
 * @param descriptor the descriptor of the bytes
 */
public record SerializedBlob(byte[] bytes, OciDescriptor descriptor) {
}
