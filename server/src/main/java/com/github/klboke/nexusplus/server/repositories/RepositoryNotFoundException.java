package com.github.klboke.nexusplus.server.repositories;

public class RepositoryNotFoundException extends RuntimeException {
  public RepositoryNotFoundException(String name) {
    super("Repository not found: " + name);
  }
}
