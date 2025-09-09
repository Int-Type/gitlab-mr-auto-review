package com.inttype.codereview.review.adapter;

import java.util.List;

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

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * SSAFY GMS API를 위한 어댑터 구현체
 *
 * <p>SSAFY GMS(Gateway Management System)를 통해 OpenAI API 호출을 수행합니다.
 * OpenAI API 형식과 호환되지만 GMS 전용 URL과 키를 사용합니다.
 * 완전한 프롬프트를 받아서 처리하는 방식을 사용합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmsAdapter implements LLMAdapter {

	private static final double DEFAULT_TEMPERATURE = 0.2;

	private final LLMProps llmProps;

	/**
	 * GMS API를 통해 코드 리뷰를 생성합니다.
	 *
	 * <p>완전한 프롬프트를 사용자 메시지로 전달하여 리뷰를 생성합니다.
	 * systemPrompt 파라미터는 사용되지 않습니다.</p>
	 *
	 * @param diffs GitLab MR의 변경사항 목록 (사용되지 않음, 호환성 유지용)
	 * @param systemPrompt 시스템 프롬프트 (사용되지 않음, 호환성 유지용)
	 * @return 생성된 리뷰 내용
	 */
	@Override
	@Retry(name = "gms")
	public Mono<String> generateReview(List<Diff> diffs, String systemPrompt) {
		if (!isAvailable()) {
			return Mono.error(new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				"GMS",
				"SSAFY GMS API 키가 설정되지 않았습니다."
			));
		}

		// systemPrompt가 실제 완전한 프롬프트인 경우 (새로운 방식)
		if (StringUtils.hasText(systemPrompt)) {
			return generateReviewWithCompletePrompt(systemPrompt);
		}

		// 레거시 호환성: systemPrompt가 비어있는 경우 에러
		return Mono.error(new LLMException(
			LLMException.ErrorType.INVALID_FORMAT,
			"GMS",
			"완전한 프롬프트가 제공되지 않았습니다."
		));
	}

	/**
	 * 완전한 프롬프트를 사용하여 GMS API로 리뷰를 생성합니다.
	 *
	 * @param completePrompt 완성된 프롬프트
	 * @return 생성된 리뷰 내용
	 */
	public Mono<String> generateReviewWithCompletePrompt(String completePrompt) {
		if (!isAvailable()) {
			return Mono.error(new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				"GMS",
				"SSAFY GMS API 키가 설정되지 않았습니다."
			));
		}

		try {
			// WebClient 생성 (GMS 전용 설정)
			WebClient webClient = createWebClient();

			// API 요청 생성 (완전한 프롬프트를 사용자 메시지로 전달)
			ChatRequest request = new ChatRequest(
				getModelName(),
				List.of(
					new ChatMessage("user", completePrompt)
				),
				DEFAULT_TEMPERATURE
			);

			log.debug("GMS API 요청 시작 - 모델: {}, 프롬프트 길이: {}",
				getModelName(), completePrompt.length());

			// API 호출 및 응답 처리
			return webClient.post()
				.uri("/chat/completions")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(ChatResponse.class)
				.map(this::extractContent)
				.onErrorMap(this::handleError);

		} catch (Exception e) {
			log.error("GMS 어댑터에서 예상치 못한 오류 발생", e);
			return Mono.error(new LLMException(
				LLMException.ErrorType.UNKNOWN,
				"GMS",
				"예상치 못한 오류: " + e.getMessage(),
				e
			));
		}
	}

	/**
	 * GMS API 전용 WebClient를 생성합니다.
	 *
	 * @return 설정된 WebClient 인스턴스
	 */
	private WebClient createWebClient() {
		String apiUrl = llmProps.getGms() != null && StringUtils.hasText(llmProps.getGms().getApiUrl())
			? llmProps.getGms().getApiUrl()
			: "https://gms.ssafy.io/gmsapi/api.openai.com/v1";

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
	 * @param response GMS API 응답
	 * @return 추출된 리뷰 내용
	 */
	private String extractContent(ChatResponse response) {
		if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"GMS",
				"API 응답이 비어있습니다."
			);
		}

		var message = response.getChoices().get(0).getMessage();
		if (message == null || !StringUtils.hasText(message.getContent())) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"GMS",
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
					"GMS",
					"인증 실패 - GMS API 키를 확인하세요.",
					error
				);
				case 429 -> new LLMException(
					LLMException.ErrorType.RATE_LIMIT_EXCEEDED,
					"GMS",
					"요청 한도 초과 - 잠시 후 다시 시도하세요.",
					error
				);
				case 500, 502, 503, 504 -> new LLMException(
					LLMException.ErrorType.SERVER_ERROR,
					"GMS",
					"서버 오류 - 잠시 후 다시 시도하세요.",
					error
				);
				default -> new LLMException(
					LLMException.ErrorType.UNKNOWN,
					"GMS",
					"HTTP " + webEx.getStatusCode() + ": " + webEx.getResponseBodyAsString(),
					error
				);
			};
		}

		return new LLMException(
			LLMException.ErrorType.NETWORK_ERROR,
			"GMS",
			"네트워크 오류: " + error.getMessage(),
			error
		);
	}

	/**
	 * API 키를 가져옵니다.
	 *
	 * @return GMS API 키
	 */
	private String getApiKey() {
		if (llmProps.getGms() != null && StringUtils.hasText(llmProps.getGms().getApiKey())) {
			return llmProps.getGms().getApiKey();
		}

		throw new LLMException(
			LLMException.ErrorType.INVALID_API_KEY,
			"GMS",
			"GMS API 키가 설정되지 않았습니다."
		);
	}

	/**
	 * 모델명을 가져옵니다.
	 *
	 * @return GMS 모델명
	 */
	private String getModelName() {
		if (llmProps.getGms() != null && StringUtils.hasText(llmProps.getGms().getModel())) {
			return llmProps.getGms().getModel();
		}

		// fallback: 기본값
		return "gpt-4o-mini";
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