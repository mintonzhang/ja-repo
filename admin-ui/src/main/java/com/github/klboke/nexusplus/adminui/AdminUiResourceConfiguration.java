package com.github.klboke.nexusplus.adminui;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminUiResourceConfiguration implements WebMvcConfigurer {
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/admin/**")
        .addResourceLocations("classpath:/META-INF/resources/admin/");
    registry
        .addResourceHandler("/login/**")
        .addResourceLocations("classpath:/META-INF/resources/login/");
  }
}
