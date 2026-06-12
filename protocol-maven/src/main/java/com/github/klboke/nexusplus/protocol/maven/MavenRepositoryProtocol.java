package com.github.klboke.nexusplus.protocol.maven;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class MavenRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.MAVEN2;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, false, false, true);
  }
}
