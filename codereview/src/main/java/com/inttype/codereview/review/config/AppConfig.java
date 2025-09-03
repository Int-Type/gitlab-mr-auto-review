package com.inttype.codereview.review.config;

import org.gitlab4j.api.GitLabApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableAspectJAutoProxy
@RequiredArgsConstructor
@EnableAsync
@EnableConfigurationProperties({GitLabProps.class, ChatGptProps.class})
public class AppConfig {
	private final ChatGptProps chatGptProps;

	@Bean
	public GitLabApi gitLabApi(GitLabProps props) {
		return new GitLabApi(props.getUrl(), props.getToken());
	}

	@Bean
	public WebClient chatGptWebClient() {
		// 대용량 응답 대비 버퍼 확장
		ExchangeStrategies strategies = ExchangeStrategies.builder()
			.codecs(c -> c.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
			.build();

		return WebClient.builder()
			.baseUrl(chatGptProps.getApiUrl())
			.defaultHeader("Authorization", "Bearer " + chatGptProps.getApiKey())
			.defaultHeader("Content-Type", "application/json")
			.exchangeStrategies(strategies)
			.build();
	}
}
