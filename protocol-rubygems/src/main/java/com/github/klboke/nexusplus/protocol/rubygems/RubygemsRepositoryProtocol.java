package com.github.klboke.nexusplus.protocol.rubygems;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class RubygemsRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.RUBYGEMS;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, true, true, true);
  }
}
