package com.inttype.codereview.review.adapter;

import java.util.List;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.inttype.codereview.review.config.LLMProps;
import com.inttype.codereview.review.dto.ChatMessage;
import com.inttype.codereview.review.dto.ChatRequest;
import com.inttype.codereview.review.dto.ChatResponse;
import com.inttype.codereview.review.exception.LLMException;
import com.inttype.codereview.review.service.PromptService;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * OpenAI API를 위한 어댑터 구현체
 *
 * <p>GPT-4, GPT-4o-mini 등 OpenAI 모델들과의 통신을 담당합니다.
 * OpenAI API 형식에 맞춰 요청/응답을 처리하고 에러를 변환합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAIAdapter implements LLMAdapter {

	private static final double DEFAULT_TEMPERATURE = 0.2;

	private final LLMProps llmProps;
	private final PromptService promptService;

	/**
	 * OpenAI API를 통해 코드 리뷰를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @param systemPrompt 시스템 프롬프트
	 * @return 생성된 리뷰 내용
	 */
	@Override
	@Retry(name = "openai")
	public Mono<String> generateReview(List<Diff> diffs, String systemPrompt) {
		if (!isAvailable()) {
			return Mono.error(new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				"OpenAI",
				"OpenAI API 키가 설정되지 않았습니다."
			));
		}

		try {
			// WebClient 생성 (OpenAI 전용 설정)
			WebClient webClient = createWebClient();

			// 프롬프트 생성
			String userPrompt = promptService.getUserPrompt(diffs);

			// API 요청 생성
			ChatRequest request = new ChatRequest(
				getModelName(),
				List.of(
					new ChatMessage("system", systemPrompt),
					new ChatMessage("user", userPrompt)
				),
				DEFAULT_TEMPERATURE
			);

			log.debug("OpenAI API 요청 시작 - 모델: {}, 파일 수: {}",
				getModelName(), diffs != null ? diffs.size() : 0);

			// API 호출 및 응답 처리
			return webClient.post()
				.uri("/chat/completions")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(ChatResponse.class)
				.map(this::extractContent)
				.onErrorMap(this::handleError);

		} catch (Exception e) {
			log.error("OpenAI 어댑터에서 예상치 못한 오류 발생", e);
			return Mono.error(new LLMException(
				LLMException.ErrorType.UNKNOWN,
				"OpenAI",
				"예상치 못한 오류: " + e.getMessage(),
				e
			));
		}
	}

	/**
	 * OpenAI API 전용 WebClient를 생성합니다.
	 *
	 * @return 설정된 WebClient 인스턴스
	 */
	private WebClient createWebClient() {
		String apiUrl = llmProps.getOpenai() != null && StringUtils.hasText(llmProps.getOpenai().getApiUrl())
			? llmProps.getOpenai().getApiUrl()
			: "https://api.openai.com/v1";

		String apiKey = getApiKey();

		return WebClient.builder()
			.baseUrl(apiUrl)
			.defaultHeader("Authorization", "Bearer " + apiKey)
			.defaultHeader("Content-Type", "application/json")
			.build();
	}

	/**
	 * API 응답에서 실제 콘텐츠를 추출합니다.
	 *
	 * @param response OpenAI API 응답
	 * @return 추출된 리뷰 내용
	 */
	private String extractContent(ChatResponse response) {
		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"OpenAI",
				"API 응답이 비어있습니다."
			);
		}

		var message = response.getChoices().get(0).getMessage();
		if (message == null || !StringUtils.hasText(message.getContent())) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"OpenAI",
				"응답에 유효한 콘텐츠가 없습니다."
			);
		}

		return message.getContent();
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
					"OpenAI",
					"인증 실패 - API 키를 확인하세요.",
					error
				);
				case 429 -> new LLMException(
					LLMException.ErrorType.RATE_LIMIT_EXCEEDED,
					"OpenAI",
					"요청 한도 초과 - 잠시 후 다시 시도하세요.",
					error
				);
				case 500, 502, 503, 504 -> new LLMException(
					LLMException.ErrorType.SERVER_ERROR,
					"OpenAI",
					"서버 오류 - 잠시 후 다시 시도하세요.",
					error
				);
				default -> new LLMException(
					LLMException.ErrorType.UNKNOWN,
					"OpenAI",
					"HTTP " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString(),
					error
				);
			};
		}

		return new LLMException(
			LLMException.ErrorType.NETWORK_ERROR,
			"OpenAI",
			"네트워크 오류: " + error.getMessage(),
			error
		);
	}

	/**
	 * API 키를 가져옵니다.
	 *
	 * @return OpenAI API 키
	 */
	private String getApiKey() {
		if (llmProps.getOpenai() != null && StringUtils.hasText(llmProps.getOpenai().getApiKey())) {
			return llmProps.getOpenai().getApiKey();
		}

		// fallback: 통합 설정에서 가져오기
		if (StringUtils.hasText(llmProps.getApiKey())) {
			return llmProps.getApiKey();
		}

		throw new LLMException(
			LLMException.ErrorType.INVALID_API_KEY,
			"OpenAI",
			"API 키가 설정되지 않았습니다."
		);
	}

	/**
	 * 모델명을 가져옵니다.
	 *
	 * @return OpenAI 모델명
	 */
	private String getModelName() {
		if (llmProps.getOpenai() != null && StringUtils.hasText(llmProps.getOpenai().getModel())) {
			return llmProps.getOpenai().getModel();
		}

		// fallback: 통합 설정에서 가져오기
		return StringUtils.hasText(llmProps.getModel()) ? llmProps.getModel() : "gpt-4o-mini";
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
