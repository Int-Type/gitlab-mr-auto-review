package com.inttype.codereview.review.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

@Data
@Validated
@ConfigurationProperties(prefix = "app.gitlab")
public class GitLabProps {
	private String url;

	private String token;

	private String webhookSecret;

	private List<String> allowedProjects = new ArrayList<>();
}
