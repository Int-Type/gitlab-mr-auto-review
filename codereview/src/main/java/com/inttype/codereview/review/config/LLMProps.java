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
@ConfigurationProperties(prefix = "app.llm")
public class LLMProps {
	private String apiUrl;
	private String apiKey;
	private String model;
	private String systemPrompt;
	
	// OpenAI 호환 API 사용 여부
	private boolean openaiCompatible = true;
	
	// 다양한 LLM 서비스별 설정
	private OpenAI openai;
	private Claude claude;
	private Gemini gemini;
	
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OpenAI {
		private String apiUrl = "https://api.openai.com/v1";
		private String apiKey;
		private String model = "gpt-4o-mini";
	}
	
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Claude {
		private String apiUrl = "https://api.anthropic.com/v1";
		private String apiKey;
		private String model = "claude-3-opus-20240229";
	}
	
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Gemini {
		private String apiUrl = "https://generativelanguage.googleapis.com/v1beta";
		private String apiKey;
		private String model = "gemini-pro";
	}
}