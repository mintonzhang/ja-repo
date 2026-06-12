package com.github.klboke.nexusplus.browseui;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class BrowseUiResourceConfiguration implements WebMvcConfigurer {
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/browse/**")
        .addResourceLocations("classpath:/META-INF/resources/browse/");
  }
}
