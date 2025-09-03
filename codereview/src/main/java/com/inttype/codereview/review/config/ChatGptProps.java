package com.inttype.codereview.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@Validated
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "app.chatgpt")
public class ChatGptProps {
	private String apiUrl;
	private String apiKey;
	private String model;
	private String systemPrompt;
}
