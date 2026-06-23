package com.github.klboke.kkrepo.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContentSelectorExpressionEvaluatorTest {

  @Test
  void evaluatesNexusStylePathRegexWithLeadingSlash() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == \"maven2\" && path =~ \"^/junit/.*\\\\.pom$\"",
        "maven-public",
        "maven2",
        "junit/junit/4.13.2/junit-4.13.2.pom"));
  }

  @Test
  void evaluatesMavenCoordinates() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "coordinate.groupId == \"junit\" && coordinate.artifactId == \"junit\""
            + " && coordinate.version == \"4.13.2\" && coordinate.extension == \"jar\"",
        "maven-public",
        "maven2",
        "junit/junit/4.13.2/junit-4.13.2.jar"));
  }

  @Test
  void evaluatesNexusMavenClassifierAndExtensionForms() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "coordinate.classifier == \"\" && coordinate.extension == \".jar\"",
        "maven-public",
        "maven2",
        "junit/junit/4.13.2/junit-4.13.2.jar"));
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "coordinate.classifier == \"sources\" && coordinate.extension == \".jar\"",
        "maven-public",
        "maven2",
        "junit/junit/4.13.2/junit-4.13.2-sources.jar"));
  }

  @Test
  void evaluatesNpmPackageVariables() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == \"npm\" && coordinate.name == \"@scope/pkg\" && package.scope == \"scope\"",
        "npm-group",
        "npm",
        "@scope/pkg/-/pkg-1.0.0.tgz"));
  }

  @Test
  void supportsBooleanGroupingAndNegation() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == \"raw\" && !(path =~ \".*private.*\")",
        "raw-hosted",
        "raw",
        "public/readme.txt"));
  }

  @Test
  void supportsNexusJexlWordOperatorsAndAssetAliases() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "component.format == 'maven2' and asset.name =~ '^/org/example/.*'",
        "maven-public",
        "maven2",
        "org/example/demo/1.0.0/demo-1.0.0.jar"));
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "asset.format == 'npm' or asset.name =^ '@scope/pkg'",
        "npm-group",
        "npm",
        "@scope/pkg/-/pkg-1.0.0.tgz"));
  }

  @Test
  void supportsNexusJexlWordNegation() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == 'raw' and not path =~ '.*private.*'",
        "raw-hosted",
        "raw",
        "public/readme.txt"));
  }

  @Test
  void supportsRepresentativeNexusCselFixtureExpressions() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == \"maven2\" && path =~ \"(?!.*-sources.*).*\"",
        "maven-public",
        "maven2",
        "junit/junit/4.13.2/junit-4.13.2.jar"));
    assertFalse(ContentSelectorExpressionEvaluator.matches(
        "format == \"maven2\" && path =~ \"(?!.*-sources.*).*\"",
        "maven-public",
        "maven2",
        "junit/junit/4.13.2/junit-4.13.2-sources.jar"));
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == \"maven2\" && ( path =~ \"^/com/acme/team/.*\" || path =~ \".*maven-metadata\\\\.xml.*\" )",
        "maven-public",
        "maven2",
        "com/acme/team/app/maven-metadata.xml"));
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "format == \"maven2\" && path =^ \"/org/sonatype/nexus\"",
        "maven-public",
        "maven2",
        "org/sonatype/nexus/nexus-repository/1.0/nexus-repository-1.0.jar"));
  }

  @Test
  void regexOperatorRequiresLiteralPattern() {
    assertFalse(ContentSelectorExpressionEvaluator.matches(
        "path =~ repository.name",
        ".*",
        "raw",
        "public/readme.txt"));
  }

  @Test
  void supportsSimpleLeadingNegativeLookaheadWithoutJdkRegex() {
    assertTrue(ContentSelectorExpressionEvaluator.matches(
        "path =~ \"(?!.*private.*).*\"",
        "raw-hosted",
        "raw",
        "public/readme.txt"));
    assertFalse(ContentSelectorExpressionEvaluator.matches(
        "path =~ \"(?!.*private.*).*\"",
        "raw-hosted",
        "raw",
        "private/secret.txt"));
  }

  @Test
  void unsupportedExpressionsFailClosed() {
    assertFalse(ContentSelectorExpressionEvaluator.matches(
        "path == ",
        "raw-hosted",
        "raw",
        "public/readme.txt"));
  }
}
