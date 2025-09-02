package com.inttype.codereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gitlab")
public record GitLabProps(String url, String token, String webhookSecret, java.util.List<String> allowedProjects) {
}
