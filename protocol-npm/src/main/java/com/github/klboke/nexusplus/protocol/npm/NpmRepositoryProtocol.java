package com.github.klboke.nexusplus.protocol.npm;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class NpmRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.NPM;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, false, false, true);
  }
}
