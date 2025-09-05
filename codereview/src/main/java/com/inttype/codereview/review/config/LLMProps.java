package com.inttype.codereview.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LLM(Large Language Model) 통합 설정 Properties
 *
 * <p>다양한 LLM 서비스(OpenAI, Claude, Gemini)의 설정을 통합 관리합니다.
 * 통합 설정이 개별 LLM 설정보다 우선순위를 가집니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Data
@Builder
@Validated
@Component
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "app.llm")
public class LLMProps {
	private String apiUrl;
	private String apiKey;
	private String model;
	private String systemPrompt;

	// OpenAI 호환 API 사용 여부
	@Builder.Default
	private boolean openaiCompatible = true;

	// 다양한 LLM 서비스별 설정
	private OpenAI openai;
	private Claude claude;
	private Gemini gemini;

	/**
	 * OpenAI 개별 설정
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OpenAI {
		@Builder.Default
		private String apiUrl = "https://api.openai.com/v1";
		private String apiKey;
		@Builder.Default
		private String model = "gpt-4o-mini";
	}

	/**
	 * Claude 개별 설정
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Claude {
		@Builder.Default
		private String apiUrl = "https://api.anthropic.com/v1";
		private String apiKey;
		@Builder.Default
		private String model = "claude-sonnet-4-20250514";
	}

	/**
	 * Gemini 개별 설정
	 */
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Gemini {
		@Builder.Default
		private String apiUrl = "https://generativelanguage.googleapis.com/v1beta";
		private String apiKey;
		@Builder.Default
		private String model = "gemini-2.5-pro";
	}
}
