package com.inttype.codereview.review.adapter;

import java.util.List;
import java.util.Map;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.inttype.codereview.review.config.LLMProps;
import com.inttype.codereview.review.exception.LLMException;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Claude API를 위한 어댑터 구현체
 *
 * <p>Claude 4 시리즈 모델들과의 통신을 담당합니다.
 * Anthropic API 형식에 맞춰 요청/응답을 처리하고 에러를 변환합니다.
 * 완전한 프롬프트를 받아서 처리하는 새로운 방식을 사용합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeAdapter implements LLMAdapter {

	private static final int MAX_TOKENS = 4096;

	private final LLMProps llmProps;

	/**
	 * Claude API를 통해 코드 리뷰를 생성합니다.
	 *
	 * <p>완전한 프롬프트를 사용자 메시지로 전달하여 리뷰를 생성합니다.
	 * systemPrompt 파라미터는 사용되지 않습니다.</p>
	 *
	 * @param diffs GitLab MR의 변경사항 목록 (사용되지 않음, 호환성 유지용)
	 * @param systemPrompt 시스템 프롬프트 (사용되지 않음, 호환성 유지용)
	 * @return 생성된 리뷰 내용
	 */
	@Override
	@Retry(name = "claude")
	public Mono<String> generateReview(List<Diff> diffs, String systemPrompt) {
		if (!isAvailable()) {
			return Mono.error(new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				"Claude",
				"Claude API 키가 설정되지 않았습니다."
			));
		}

		// systemPrompt가 실제 완전한 프롬프트인 경우 (새로운 방식)
		if (StringUtils.hasText(systemPrompt)) {
			return generateReviewWithCompletePrompt(systemPrompt);
		}

		// 레거시 호환성: systemPrompt가 비어있는 경우 에러
		return Mono.error(new LLMException(
			LLMException.ErrorType.INVALID_FORMAT,
			"Claude",
			"완전한 프롬프트가 제공되지 않았습니다."
		));
	}

	/**
	 * 완전한 프롬프트를 사용하여 Claude API로 리뷰를 생성합니다.
	 *
	 * @param completePrompt 완성된 프롬프트
	 * @return 생성된 리뷰 내용
	 */
	public Mono<String> generateReviewWithCompletePrompt(String completePrompt) {
		if (!isAvailable()) {
			return Mono.error(new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				"Claude",
				"Claude API 키가 설정되지 않았습니다."
			));
		}

		try {
			// WebClient 생성 (Claude 전용 설정)
			WebClient webClient = createWebClient();

			// Claude API 요청 형식으로 변환 (완전한 프롬프트를 사용자 메시지로 전달)
			Map<String, Object> request = Map.of(
				"model", getModelName(),
				"max_tokens", MAX_TOKENS,
				"messages", List.of(
					Map.of(
						"role", "user",
						"content", completePrompt
					)
				)
			);

			log.debug("Claude API 요청 시작 - 모델: {}, 프롬프트 길이: {}",
				getModelName(), completePrompt.length());

			// API 호출 및 응답 처리
			return webClient.post()
				.uri("/messages")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(Map.class)
				.map(this::extractContent)
				.onErrorMap(this::handleError);

		} catch (Exception e) {
			log.error("Claude 어댑터에서 예상치 못한 오류 발생", e);
			return Mono.error(new LLMException(
				LLMException.ErrorType.UNKNOWN,
				"Claude",
				"예상치 못한 오류: " + e.getMessage(),
				e
			));
		}
	}

	/**
	 * Claude API 전용 WebClient를 생성합니다.
	 * Claude API는 x-api-key 헤더를 사용하며, 최신 API 버전을 기본값으로 사용합니다.
	 *
	 * @return 설정된 WebClient 인스턴스
	 */
	private WebClient createWebClient() {
		String apiUrl = llmProps.getClaude() != null && StringUtils.hasText(llmProps.getClaude().getApiUrl())
			? llmProps.getClaude().getApiUrl()
			: "https://api.anthropic.com/v1";

		String apiKey = getApiKey();

		return WebClient.builder()
			.baseUrl(apiUrl)
			.defaultHeader("x-api-key", apiKey)  // Claude는 x-api-key 사용
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	/**
	 * Claude API 응답에서 실제 콘텐츠를 추출합니다.
	 * Claude API는 OpenAI와 다른 응답 구조를 가집니다.
	 *
	 * @param response Claude API 응답
	 * @return 추출된 리뷰 내용
	 */
	@SuppressWarnings("unchecked")
	private String extractContent(Map<String, Object> response) {
		if (response == null) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Claude",
				"API 응답이 비어있습니다."
			);
		}

		// Claude API 응답 구조: { "content": [{"type": "text", "text": "..."}] }
		Object contentObj = response.get("content");
		if (!(contentObj instanceof List)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Claude",
				"응답 형식이 올바르지 않습니다: content가 배열이 아님"
			);
		}

		List<Map<String, Object>> contentList = (List<Map<String, Object>>)contentObj;
		if (contentList.isEmpty()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Claude",
				"응답에 콘텐츠가 없습니다."
			);
		}

		Map<String, Object> firstContent = contentList.get(0);
		Object textObj = firstContent.get("text");

		if (!(textObj instanceof String) || !StringUtils.hasText((String)textObj)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Claude",
				"응답에 유효한 텍스트가 없습니다."
			);
		}

		return (String)textObj;
	}

	/**
	 * WebClient 에러를 LLM 예외로 변환합니다.
	 *
	 * @param error 원본 에러
	 * @return 변환된 LLM 예외
	 */
	private LLMException handleError(Throwable error) {
		if (error instanceof WebClientResponseException webEx) {
			return switch (webEx.getStatusCode().value()) {
				case 401 -> new LLMException(
					LLMException.ErrorType.INVALID_API_KEY,
					"Claude",
					"인증 실패 - API 키를 확인하세요.",
					error
				);
				case 429 -> new LLMException(
					LLMException.ErrorType.RATE_LIMIT_EXCEEDED,
					"Claude",
					"요청 한도 초과 - 잠시 후 다시 시도하세요.",
					error
				);
				case 500, 502, 503, 504 -> new LLMException(
					LLMException.ErrorType.SERVER_ERROR,
					"Claude",
					"서버 오류 - 잠시 후 다시 시도하세요.",
					error
				);
				default -> new LLMException(
					LLMException.ErrorType.UNKNOWN,
					"Claude",
					"HTTP " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString(),
					error
				);
			};
		}

		return new LLMException(
			LLMException.ErrorType.NETWORK_ERROR,
			"Claude",
			"네트워크 오류: " + error.getMessage(),
			error
		);
	}

	/**
	 * API 키를 가져옵니다.
	 *
	 * @return Claude API 키
	 */
	private String getApiKey() {
		if (llmProps.getClaude() != null && StringUtils.hasText(llmProps.getClaude().getApiKey())) {
			return llmProps.getClaude().getApiKey();
		}

		throw new LLMException(
			LLMException.ErrorType.INVALID_API_KEY,
			"Claude",
			"API 키가 설정되지 않았습니다."
		);
	}

	/**
	 * 모델명을 가져옵니다.
	 *
	 * @return Claude 모델명
	 */
	private String getModelName() {
		if (llmProps.getClaude() != null && StringUtils.hasText(llmProps.getClaude().getModel())) {
			return llmProps.getClaude().getModel();
		}

		// fallback: 통합 설정에서 가져오기 (최신 모델로 업데이트)
		return StringUtils.hasText(llmProps.getModel()) ? llmProps.getModel() : "claude-sonnet-4-20250514";
	}

	@Override
	public String getSupportedModel() {
		return getModelName();
	}

	@Override
	public boolean isAvailable() {
		try {
			String apiKey = getApiKey();
			return StringUtils.hasText(apiKey);
		} catch (LLMException e) {
			return false;
		}
	}
}
