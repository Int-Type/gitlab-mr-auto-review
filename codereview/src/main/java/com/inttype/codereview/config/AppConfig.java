package com.inttype.codereview.config;

import org.gitlab4j.api.GitLabApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GitLabProps.class)
public class AppConfig {
	@Bean
	public GitLabApi gitLabApi(GitLabProps props) {
		return new GitLabApi(props.url(), props.token());
	}
}
