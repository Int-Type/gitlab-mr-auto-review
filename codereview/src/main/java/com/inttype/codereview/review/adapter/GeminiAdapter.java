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
 * Google Gemini API를 위한 어댑터 구현체
 *
 * <p>Gemini Pro 등 Google의 Gemini 모델들과의 통신을 담당합니다.
 * Google AI API 형식에 맞춰 요청/응답을 처리하고 에러를 변환합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiAdapter implements LLMAdapter {

	private final LLMProps llmProps;
	private final PromptService promptService;

	/**
	 * Gemini API를 통해 코드 리뷰를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @param systemPrompt 시스템 프롬프트
	 * @return 생성된 리뷰 내용
	 */
	@Override
	@Retry(name = "gemini")
	public Mono<String> generateReview(List<Diff> diffs, String systemPrompt) {
		if (!isAvailable()) {
			return Mono.error(new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				"Gemini",
				"Gemini API 키가 설정되지 않았습니다."
			));
		}

		try {
			// WebClient 생성 (Gemini 전용 설정)
			WebClient webClient = createWebClient();

			// 프롬프트 생성 (Gemini는 시스템 프롬프트를 사용자 프롬프트에 포함)
			String userPrompt = promptService.getUserPrompt(diffs);
			String combinedPrompt = combinePrompts(systemPrompt, userPrompt);

			// Gemini API 요청 형식으로 변환
			Map<String, Object> request = Map.of(
				"contents", List.of(
					Map.of(
						"parts", List.of(
							Map.of("text", combinedPrompt)
						)
					)
				),
				"generationConfig", Map.of(
					"temperature", 0.2,
					"topK", 40,
					"topP", 0.95,
					"maxOutputTokens", 4096
				)
			);

			log.debug("Gemini API 요청 시작 - 모델: {}, 파일 수: {}",
				getModelName(), diffs != null ? diffs.size() : 0);

			// API 호출 및 응답 처리 (API 키를 URL 파라미터로 추가)
			String uri = String.format("/models/%s:generateContent?key=%s", getModelName(), getApiKey());
			return webClient.post()
				.uri(uri)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(Map.class)
				.map(this::extractContent)
				.onErrorMap(this::handleError);

		} catch (Exception e) {
			log.error("Gemini 어댑터에서 예상치 못한 오류 발생", e);
			return Mono.error(new LLMException(
				LLMException.ErrorType.UNKNOWN,
				"Gemini",
				"예상치 못한 오류: " + e.getMessage(),
				e
			));
		}
	}

	/**
	 * Gemini API 전용 WebClient를 생성합니다.
	 * Gemini API는 URL 파라미터로 API 키를 전달합니다.
	 *
	 * @return 설정된 WebClient 인스턴스
	 */
	private WebClient createWebClient() {
		String apiUrl = llmProps.getGemini() != null && StringUtils.hasText(llmProps.getGemini().getApiUrl())
			? llmProps.getGemini().getApiUrl()
			: "https://generativelanguage.googleapis.com/v1beta";

		return WebClient.builder()
			.baseUrl(apiUrl)
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	/**
	 * 시스템 프롬프트와 사용자 프롬프트를 결합합니다.
	 * Gemini는 별도의 시스템 프롬프트를 지원하지 않으므로 하나로 결합합니다.
	 *
	 * @param systemPrompt 시스템 프롬프트
	 * @param userPrompt 사용자 프롬프트
	 * @return 결합된 프롬프트
	 */
	private String combinePrompts(String systemPrompt, String userPrompt) {
		return String.format("""
            %s
            
            ---
            
            %s
            """, systemPrompt, userPrompt);
	}

	/**
	 * Gemini API 응답에서 실제 콘텐츠를 추출합니다.
	 * Gemini API는 고유한 응답 구조를 가집니다.
	 *
	 * @param response Gemini API 응답
	 * @return 추출된 리뷰 내용
	 */
	@SuppressWarnings("unchecked")
	private String extractContent(Map<String, Object> response) {
		if (response == null) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
				"API 응답이 비어있습니다."
			);
		}

		// Gemini API 응답 구조: { "candidates": [{"content": {"parts": [{"text": "..."}]}}] }
		Object candidatesObj = response.get("candidates");
		if (!(candidatesObj instanceof List)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
				"응답 형식이 올바르지 않습니다: candidates가 배열이 아님"
			);
		}

		List<Map<String, Object>> candidates = (List<Map<String, Object>>) candidatesObj;
		if (candidates.isEmpty()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
				"응답에 candidates가 없습니다."
			);
		}

		Map<String, Object> firstCandidate = candidates.get(0);
		Object contentObj = firstCandidate.get("content");
		if (!(contentObj instanceof Map)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
				"응답 형식이 올바르지 않습니다: content가 객체가 아님"
			);
		}

		Map<String, Object> content = (Map<String, Object>) contentObj;
		Object partsObj = content.get("parts");
		if (!(partsObj instanceof List)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
				"응답 형식이 올바르지 않습니다: parts가 배열이 아님"
			);
		}

		List<Map<String, Object>> parts = (List<Map<String, Object>>) partsObj;
		if (parts.isEmpty()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
				"응답에 parts가 없습니다."
			);
		}

		Map<String, Object> firstPart = parts.get(0);
		Object textObj = firstPart.get("text");

		if (!(textObj instanceof String) || !StringUtils.hasText((String) textObj)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Gemini",
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
				case 400 -> {
					String body = webEx.getResponseBodyAsString();
					if (body.contains("API_KEY_INVALID")) {
						yield new LLMException(
							LLMException.ErrorType.INVALID_API_KEY,
							"Gemini",
							"유효하지 않은 API 키입니다.",
							error
						);
					}
					yield new LLMException(
						LLMException.ErrorType.INVALID_FORMAT,
						"Gemini",
						"잘못된 요청: " + body,
						error
					);
				}
				case 429 -> new LLMException(
					LLMException.ErrorType.RATE_LIMIT_EXCEEDED,
					"Gemini",
					"요청 한도 초과 - 잠시 후 다시 시도하세요.",
					error
				);
				case 500, 502, 503, 504 -> new LLMException(
					LLMException.ErrorType.SERVER_ERROR,
					"Gemini",
					"서버 오류 - 잠시 후 다시 시도하세요.",
					error
				);
				default -> new LLMException(
					LLMException.ErrorType.UNKNOWN,
					"Gemini",
					"HTTP " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString(),
					error
				);
			};
		}

		return new LLMException(
			LLMException.ErrorType.NETWORK_ERROR,
			"Gemini",
			"네트워크 오류: " + error.getMessage(),
			error
		);
	}

	/**
	 * API 키를 가져옵니다.
	 *
	 * @return Gemini API 키
	 */
	private String getApiKey() {
		if (llmProps.getGemini() != null && StringUtils.hasText(llmProps.getGemini().getApiKey())) {
			return llmProps.getGemini().getApiKey();
		}

		throw new LLMException(
			LLMException.ErrorType.INVALID_API_KEY,
			"Gemini",
			"API 키가 설정되지 않았습니다."
		);
	}

	/**
	 * 모델명을 가져옵니다.
	 *
	 * @return Gemini 모델명
	 */
	private String getModelName() {
		if (llmProps.getGemini() != null && StringUtils.hasText(llmProps.getGemini().getModel())) {
			return llmProps.getGemini().getModel();
		}

		// fallback: 통합 설정에서 가져오기 (기본값을 최신 모델로 업데이트)
		return StringUtils.hasText(llmProps.getModel()) ? llmProps.getModel() : "gemini-2.5-pro";
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
