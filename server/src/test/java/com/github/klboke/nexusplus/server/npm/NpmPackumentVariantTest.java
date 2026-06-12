package com.github.klboke.nexusplus.server.npm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NpmPackumentVariantTest {

  @Test
  void absentOrGenericAcceptUsesFullPackument() {
    assertEquals(NpmPackumentVariant.FULL, NpmPackumentVariant.fromAccept(null));
    assertEquals(NpmPackumentVariant.FULL, NpmPackumentVariant.fromAccept("application/json"));
  }

  @Test
  void npmInstallV1AcceptUsesAbbreviatedPackument() {
    assertEquals(NpmPackumentVariant.INSTALL_V1,
        NpmPackumentVariant.fromAccept("application/vnd.npm.install-v1+json"));
    assertEquals(NpmPackumentVariant.INSTALL_V1,
        NpmPackumentVariant.fromAccept("application/json, application/vnd.npm.install-v1+json; q=1.0"));
    assertEquals(NpmPackumentVariant.INSTALL_V1,
        NpmPackumentVariant.fromAccept("Application/Vnd.Npm.Install-V1+Json"));
  }
}
