package com.github.klboke.nexusplus.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RepositoryRecipesTest {
  @Test
  void exposesNexusRecipeTriplesForNugetRubygemsAndYum() {
    Map<String, RepositoryRecipe> recipes = RepositoryRecipes.list().stream()
        .collect(Collectors.toMap(RepositoryRecipe::name, recipe -> recipe));

    assertRecipe(recipes, "nuget-hosted", RepositoryFormat.NUGET, RepositoryType.HOSTED);
    assertRecipe(recipes, "nuget-proxy", RepositoryFormat.NUGET, RepositoryType.PROXY);
    assertRecipe(recipes, "nuget-group", RepositoryFormat.NUGET, RepositoryType.GROUP);
    assertRecipe(recipes, "rubygems-hosted", RepositoryFormat.RUBYGEMS, RepositoryType.HOSTED);
    assertRecipe(recipes, "rubygems-proxy", RepositoryFormat.RUBYGEMS, RepositoryType.PROXY);
    assertRecipe(recipes, "rubygems-group", RepositoryFormat.RUBYGEMS, RepositoryType.GROUP);
    assertRecipe(recipes, "yum-hosted", RepositoryFormat.YUM, RepositoryType.HOSTED);
    assertRecipe(recipes, "yum-proxy", RepositoryFormat.YUM, RepositoryType.PROXY);
    assertRecipe(recipes, "yum-group", RepositoryFormat.YUM, RepositoryType.GROUP);
  }

  private static void assertRecipe(
      Map<String, RepositoryRecipe> recipes,
      String name,
      RepositoryFormat format,
      RepositoryType type) {
    assertTrue(recipes.containsKey(name), "missing " + name);
    assertEquals(format, recipes.get(name).format());
    assertEquals(type, recipes.get(name).type());
  }
}
