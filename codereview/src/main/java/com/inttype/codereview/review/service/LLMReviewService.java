package com.inttype.codereview.review.service;

import java.util.List;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Service;

import com.inttype.codereview.review.adapter.LLMAdapter;
import com.inttype.codereview.review.adapter.LLMAdapterFactory;
import com.inttype.codereview.review.exception.LLMException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 통합 LLM을 통한 코드 리뷰 생성 서비스
 *
 * <p>통합 LLM 모드에서 사용되며, 하나의 범용 프롬프트로
 * OpenAI, Claude, Gemini 등 다양한 LLM API를 통합된 인터페이스로 제공합니다.
 * 프롬프트 관리는 PromptService에 위임하여 중앙 집중 관리됩니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewService {

	private final LLMAdapterFactory adapterFactory;
	private final PromptService promptService;

	/**
	 * 서비스 초기화 시 현재 LLM 설정 정보를 로그에 출력합니다.
	 */
	@PostConstruct
	public void initialize() {
		log.info("LLMReviewService 초기화 중...");
		adapterFactory.logCurrentConfiguration();
	}

	/**
	 * GitLab MR의 변경사항을 분석하여 통합 LLM 기반 자동 코드 리뷰를 생성합니다.
	 *
	 * <p>설정된 모델에 따라 자동으로 적절한 LLM 어댑터를 선택하여 리뷰를 생성합니다.
	 * 시스템 프롬프트는 PromptService에서 하드코딩된 통합 모드 프롬프트를 사용합니다.</p>
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 생성된 코드 리뷰 내용을 담은 Mono 객체
	 * @throws LLMException LLM API 호출 중 오류가 발생한 경우
	 */
	public Mono<String> generateReview(List<Diff> diffs) {
		try {
			// 1. 적절한 LLM 어댑터 선택
			LLMAdapter adapter = adapterFactory.getAdapter();

			// 2. PromptService를 통해 통합 모드 완전한 프롬프트 준비
			String completePrompt = promptService.buildIntegratedPrompt(diffs);

			// 3. 변경사항 유효성 검사
			if (diffs == null || diffs.isEmpty()) {
				return Mono.just(promptService.getNoChangesMessage());
			}

			log.debug("통합 LLM 리뷰 생성 시작 - 어댑터: {}, 파일 수: {}",
				adapter.getClass().getSimpleName(), diffs.size());

			// 4. 리뷰 생성 (완전한 프롬프트를 사용자 프롬프트로 전달)
			return adapter.generateReview(diffs, "")
				.doOnSuccess(review -> log.debug("통합 LLM 리뷰 생성 완료 - 길이: {}", review.length()))
				.doOnError(error -> log.error("통합 LLM 리뷰 생성 실패", error));

		} catch (LLMException e) {
			log.error("LLM 어댑터 선택/설정 오류: {}", e.getMessage());
			return Mono.error(e);
		} catch (Exception e) {
			log.error("예상치 못한 오류 발생", e);
			return Mono.error(new LLMException(
				LLMException.ErrorType.UNKNOWN,
				"LLMReviewService",
				"예상치 못한 오류: " + e.getMessage(),
				e
			));
		}
	}

	/**
	 * 현재 사용 중인 LLM 모델 정보를 반환합니다.
	 *
	 * @return 현재 LLM 모델명
	 */
	public String getCurrentModel() {
		try {
			LLMAdapter adapter = adapterFactory.getAdapter();
			return adapter.getSupportedModel();
		} catch (Exception e) {
			log.warn("현재 모델 정보 조회 실패", e);
			return "알 수 없음";
		}
	}

	/**
	 * 현재 사용 중인 어댑터 타입을 반환합니다.
	 *
	 * @return 어댑터 타입 (예: "OpenAI", "Claude", "Gemini")
	 */
	public String getCurrentAdapterType() {
		try {
			LLMAdapter adapter = adapterFactory.getAdapter();
			return adapter.getClass().getSimpleName().replace("Adapter", "");
		} catch (Exception e) {
			log.warn("현재 어댑터 타입 조회 실패", e);
			return "알 수 없음";
		}
	}

	/**
	 * 사용 가능한 LLM 어댑터 목록을 반환합니다.
	 *
	 * @return 사용 가능한 어댑터 목록
	 */
	public List<String> getAvailableAdapters() {
		return adapterFactory.getAvailableAdapters()
			.stream()
			.map(adapter -> adapter.getClass().getSimpleName().replace("Adapter", ""))
			.toList();
	}

	/**
	 * LLM 서비스의 현재 상태를 확인합니다.
	 *
	 * @return 서비스 사용 가능 여부
	 */
	public boolean isServiceAvailable() {
		try {
			LLMAdapter adapter = adapterFactory.getAdapter();
			return adapter.isAvailable();
		} catch (Exception e) {
			log.debug("LLM 서비스 상태 확인 실패", e);
			return false;
		}
	}

	/**
	 * 현재 서비스 상태 정보를 맵 형태로 반환합니다.
	 * (헬스체크나 관리 API에서 사용 가능)
	 *
	 * @return 서비스 상태 정보
	 */
	public java.util.Map<String, Object> getServiceStatus() {
		java.util.Map<String, Object> status = new java.util.HashMap<>();

		try {
			status.put("available", isServiceAvailable());
			status.put("currentModel", getCurrentModel());
			status.put("currentAdapter", getCurrentAdapterType());
			status.put("availableAdapters", getAvailableAdapters());
			status.put("promptMode", "integrated");
			status.put("promptSource", "hardcoded");

			// PromptService 상태 정보 병합
			status.putAll(promptService.getPromptStatus());
		} catch (Exception e) {
			status.put("available", false);
			status.put("error", e.getMessage());
		}

		return status;
	}
}
