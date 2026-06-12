package com.github.klboke.nexusplus.core;

public record ProtocolCapability(
    boolean hostedRead,
    boolean hostedWrite,
    boolean proxyRead,
    boolean groupRead,
    boolean nexusPathCompatible) {
}
