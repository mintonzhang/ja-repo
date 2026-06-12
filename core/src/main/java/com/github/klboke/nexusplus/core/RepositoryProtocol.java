package com.github.klboke.nexusplus.core;

public interface RepositoryProtocol {
  RepositoryFormat format();

  ProtocolCapability capability();
}
