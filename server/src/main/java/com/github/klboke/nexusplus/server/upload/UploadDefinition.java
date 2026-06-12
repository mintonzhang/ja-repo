package com.github.klboke.nexusplus.server.upload;

import java.util.List;

public record UploadDefinition(
    String format,
    boolean multipleUpload,
    List<UploadFieldDefinition> componentFields,
    List<UploadFieldDefinition> assetFields) {
}
