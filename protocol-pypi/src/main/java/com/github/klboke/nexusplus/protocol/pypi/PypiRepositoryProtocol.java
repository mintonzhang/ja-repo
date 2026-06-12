package com.github.klboke.nexusplus.protocol.pypi;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class PypiRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.PYPI;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, true, true, true);
  }
}
