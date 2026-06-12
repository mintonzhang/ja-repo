package com.github.klboke.nexusplus.protocol.yum;

import com.github.klboke.nexusplus.core.ProtocolCapability;
import com.github.klboke.nexusplus.core.RepositoryFormat;
import com.github.klboke.nexusplus.core.RepositoryProtocol;

public final class YumRepositoryProtocol implements RepositoryProtocol {
  @Override
  public RepositoryFormat format() {
    return RepositoryFormat.YUM;
  }

  @Override
  public ProtocolCapability capability() {
    return new ProtocolCapability(true, true, true, true, true);
  }
}
