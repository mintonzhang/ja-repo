package com.github.klboke.nexusplus.server.repositories;

import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryType;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.GroupSettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.HostedSettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.ProxySettings;
import com.github.klboke.nexusplus.server.repositories.RepositoryCommands.RawSettings;

public record RepositoryView(
    Long id,
    String name,
    String recipe,
    RepositoryFormat format,
    RepositoryType type,
    boolean online,
    String blobStoreName,
    boolean strictContentTypeValidation,
    String url,
    HostedSettings hosted,
    ProxySettings proxy,
    RawSettings raw,
    GroupSettings group) {
}
