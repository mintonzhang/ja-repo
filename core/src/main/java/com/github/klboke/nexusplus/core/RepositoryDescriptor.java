package com.github.klboke.nexusplus.core;

public record RepositoryDescriptor(
    String name,
    RepositoryFormat format,
    RepositoryType type,
    boolean online) {
}
