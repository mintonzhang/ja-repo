package com.github.klboke.nexusplus.protocol.goartifact;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class GoRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.GO;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(false, false, true, true, true);
  }
}
