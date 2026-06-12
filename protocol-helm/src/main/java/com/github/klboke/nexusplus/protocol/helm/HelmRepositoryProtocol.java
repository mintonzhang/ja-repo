package com.github.klboke.nexusplus.protocol.helm;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class HelmRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.HELM;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, true, false, true);
  }
}
