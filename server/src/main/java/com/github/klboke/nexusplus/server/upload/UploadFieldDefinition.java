package com.github.klboke.nexusplus.server.upload;

public record UploadFieldDefinition(
    String name,
    String type,
    String description,
    boolean optional,
    String group) {
}
