package com.inttype.codereview.review.adapter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.inttype.codereview.review.config.LLMProps;
import com.inttype.codereview.review.exception.LLMException;
import com.inttype.codereview.review.service.PromptService;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Claude API를 위한 어댑터 구현체
 *
 * <p>Claude 3 시리즈 모델들과의 통신을 담당합니다.
 * Anthropic API 형식에 맞춰 요청/응답을 처리하고 에러를 변환합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeAdapter implements LLMAdapter {

	private static final int MAX_TOKENS = 4096;
	/**
	 * Claude API 스키마 버전
	 * Claude API는 anthropic-version 헤더를 통해 API 스키마 버전을 지정해야 합니다.
	 * 이 버전에 따라 요청/응답 형식이 달라질 수 있습니다.
	 */
	private static final String API_VERSION = "2023-06-01";

	private final LLMProps llmProps;
	private final PromptService promptService;

	/**
	 * Claude API를 통해 코드 리뷰를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @param systemPrompt 시스템 프롬프트
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

		try {
			// WebClient 생성 (Claude 전용 설정)
			WebClient webClient = createWebClient();

			// 프롬프트 생성
			String userPrompt = promptService.getUserPrompt(diffs);

			// Claude API 요청 형식으로 변환
			Map<String, Object> request = Map.of(
				"model", getModelName(),
				"max_tokens", MAX_TOKENS,
				"system", systemPrompt,
				"messages", List.of(
					Map.of(
						"role", "user",
						"content", userPrompt
					)
				)
			);

			log.debug("Claude API 요청 시작 - 모델: {}, 파일 수: {}",
				getModelName(), diffs != null ? diffs.size() : 0);

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
	 * Claude API는 x-api-key 헤더와 anthropic-version 헤더를 사용합니다.
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
			.defaultHeader("anthropic-version", API_VERSION)  // API 스키마 버전 필수
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

		List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
		if (contentList.isEmpty()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Claude",
				"응답에 콘텐츠가 없습니다."
			);
		}

		Map<String, Object> firstContent = contentList.get(0);
		Object textObj = firstContent.get("text");

		if (!(textObj instanceof String) || !StringUtils.hasText((String) textObj)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Claude",
				"응답에 유효한 텍스트가 없습니다."
			);
		}

		return (String) textObj;
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

		// fallback: 통합 설정에서 가져오기
		return StringUtils.hasText(llmProps.getModel()) ? llmProps.getModel() : "claude-3-opus-20240229";
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
