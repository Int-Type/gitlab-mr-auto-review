package com.inttype.codereview.review.adapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.inttype.codereview.review.config.LLMProps;
import com.inttype.codereview.review.exception.LLMException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM 어댑터들을 관리하고 적절한 어댑터를 선택하는 팩토리 클래스
 *
 * <p>모델명을 기반으로 자동으로 적절한 LLM 어댑터를 선택하여 반환합니다.
 * 각 어댑터의 가용성도 확인하여 안전한 사용을 보장합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMAdapterFactory {

	private final OpenAIAdapter openAIAdapter;
	private final ClaudeAdapter claudeAdapter;
	private final GeminiAdapter geminiAdapter;
	private final GmsAdapter gmsAdapter;
	private final LLMProps llmProps;

	// 모델명 패턴을 기반으로 어댑터를 매핑하는 맵
	private static final Map<String, String> MODEL_TO_ADAPTER = new HashMap<>();

	static {
		// OpenAI 모델들
		MODEL_TO_ADAPTER.put("gpt-3.5", "openai");
		MODEL_TO_ADAPTER.put("gpt-4", "openai");
		MODEL_TO_ADAPTER.put("gpt-4o", "openai");
		MODEL_TO_ADAPTER.put("text-davinci", "openai");

		// Claude 모델들
		MODEL_TO_ADAPTER.put("claude-3", "claude");
		MODEL_TO_ADAPTER.put("claude-2", "claude");
		MODEL_TO_ADAPTER.put("claude-instant", "claude");

		// Gemini 모델들
		MODEL_TO_ADAPTER.put("gemini-pro", "gemini");
		MODEL_TO_ADAPTER.put("gemini-1.5", "gemini");
		MODEL_TO_ADAPTER.put("bison", "gemini");
	}

	/**
	 * 설정된 모델에 적합한 LLM 어댑터를 반환합니다.
	 *
	 * @return 선택된 LLM 어댑터
	 * @throws LLMException 적절한 어댑터를 찾을 수 없거나 사용 불가능한 경우
	 */
	public LLMAdapter getAdapter() {
		String model = getCurrentModel();

		if (!StringUtils.hasText(model)) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Factory",
				"모델이 설정되지 않았습니다."
			);
		}

		String adapterType = determineAdapterType(model);
		LLMAdapter adapter = createAdapter(adapterType);

		if (!adapter.isAvailable()) {
			throw new LLMException(
				LLMException.ErrorType.INVALID_API_KEY,
				adapterType.toUpperCase(),
				String.format("%s 어댑터를 사용할 수 없습니다. API 키 및 설정을 확인하세요.", adapterType.toUpperCase())
			);
		}

		log.info("LLM 어댑터 선택됨 - 모델: {}, 어댑터: {}", model, adapterType.toUpperCase());
		return adapter;
	}

	/**
	 * 사용 가능한 모든 어댑터 목록을 반환합니다.
	 *
	 * @return 사용 가능한 어댑터 목록
	 */
	public List<LLMAdapter> getAvailableAdapters() {
		return List.of(openAIAdapter, claudeAdapter, geminiAdapter, gmsAdapter)
			.stream()
			.filter(LLMAdapter::isAvailable)
			.toList();
	}

	/**
	 * 특정 어댑터 타입이 사용 가능한지 확인합니다.
	 *
	 * @param adapterType 어댑터 타입 ("openai", "claude", "gemini", "gms")
	 * @return 사용 가능한 경우 true
	 */
	public boolean isAdapterAvailable(String adapterType) {
		try {
			LLMAdapter adapter = createAdapter(adapterType);
			return adapter.isAvailable();
		} catch (Exception e) {
			log.warn("어댑터 가용성 확인 실패: {}", adapterType, e);
			return false;
		}
	}

	/**
	 * 현재 설정된 모델명을 가져옵니다.
	 * 우선순위: 통합 설정 > 개별 어댑터 설정
	 *
	 * @return 현재 모델명
	 */
	private String getCurrentModel() {
		// 1. 통합 설정에서 모델 확인
		if (StringUtils.hasText(llmProps.getModel())) {
			return llmProps.getModel();
		}

		// 2. 개별 어댑터 설정에서 모델 확인 (사용 가능한 것 우선)
		if (llmProps.getOpenai() != null &&
			StringUtils.hasText(llmProps.getOpenai().getApiKey()) &&
			StringUtils.hasText(llmProps.getOpenai().getModel())) {
			return llmProps.getOpenai().getModel();
		}

		if (llmProps.getClaude() != null &&
			StringUtils.hasText(llmProps.getClaude().getApiKey()) &&
			StringUtils.hasText(llmProps.getClaude().getModel())) {
			return llmProps.getClaude().getModel();
		}

		if (llmProps.getGemini() != null &&
			StringUtils.hasText(llmProps.getGemini().getApiKey()) &&
			StringUtils.hasText(llmProps.getGemini().getModel())) {
			return llmProps.getGemini().getModel();
		}

		if (llmProps.getGms() != null &&
			StringUtils.hasText(llmProps.getGms().getApiKey()) &&
			StringUtils.hasText(llmProps.getGms().getModel())) {
			return llmProps.getGms().getModel();
		}

		// 3. fallback: 기본값
		return "gpt-4o-mini";
	}

	/**
	 * 모델명을 기반으로 적절한 어댑터 타입을 결정합니다.
	 *
	 * @param model 모델명
	 * @return 어댑터 타입 ("openai", "claude", "gemini", "gms")
	 */
	private String determineAdapterType(String model) {
		if (!StringUtils.hasText(model)) {
			return "openai"; // 기본값
		}

		String lowerModel = model.toLowerCase();

		// 정확한 매칭 먼저 시도
		for (Map.Entry<String, String> entry : MODEL_TO_ADAPTER.entrySet()) {
			if (lowerModel.contains(entry.getKey().toLowerCase())) {
				return entry.getValue();
			}
		}

		// 패턴 매칭으로 fallback
		if (lowerModel.contains("gms") || lowerModel.contains("ssafy")) {
			return "gms";
		} else if (lowerModel.contains("gpt") || lowerModel.contains("openai")) {
			return "openai";
		} else if (lowerModel.contains("claude") || lowerModel.contains("anthropic")) {
			return "claude";
		} else if (lowerModel.contains("gemini") || lowerModel.contains("bison") || lowerModel.contains("palm")) {
			return "gemini";
		}

		// 최종 fallback: OpenAI
		log.warn("알 수 없는 모델: {}. OpenAI 어댑터로 fallback합니다.", model);
		return "openai";
	}

	/**
	 * 어댑터 타입에 따라 실제 어댑터 인스턴스를 생성합니다.
	 *
	 * @param adapterType 어댑터 타입
	 * @return LLM 어댑터 인스턴스
	 * @throws LLMException 지원하지 않는 어댑터 타입인 경우
	 */
	private LLMAdapter createAdapter(String adapterType) {
		return switch (adapterType.toLowerCase()) {
			case "openai" -> openAIAdapter;
			case "claude" -> claudeAdapter;
			case "gemini" -> geminiAdapter;
			case "gms" -> gmsAdapter;
			default -> throw new LLMException(
				LLMException.ErrorType.INVALID_FORMAT,
				"Factory",
				"지원하지 않는 어댑터 타입: " + adapterType
			);
		};
	}

	/**
	 * 현재 설정 정보를 로그로 출력합니다. (디버깅 용도)
	 */
	public void logCurrentConfiguration() {
		String currentModel = getCurrentModel();
		String adapterType = determineAdapterType(currentModel);

		log.info("=== LLM 설정 정보 ===");
		log.info("현재 모델: {}", currentModel);
		log.info("선택된 어댑터: {}", adapterType.toUpperCase());
		log.info("OpenAI 사용 가능: {}", isAdapterAvailable("openai"));
		log.info("Claude 사용 가능: {}", isAdapterAvailable("claude"));
		log.info("Gemini 사용 가능: {}", isAdapterAvailable("gemini"));
		log.info("GMS 사용 가능: {}", isAdapterAvailable("gms"));
		log.info("==================");
	}
}
