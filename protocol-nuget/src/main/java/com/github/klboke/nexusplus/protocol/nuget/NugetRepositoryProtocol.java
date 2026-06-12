package com.github.klboke.nexusplus.protocol.nuget;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class NugetRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.NUGET;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, true, true, true);
  }
}
