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
 * 다양한 LLM을 통한 코드 리뷰 생성 서비스
 *
 * <p>어댑터 패턴을 사용하여 OpenAI, Claude, Gemini 등 다양한 LLM API를
 * 통합된 인터페이스로 제공합니다. 모델명만 변경하면 자동으로 적절한 어댑터가 선택됩니다.</p>
 *
 * <p>프롬프트 구성은 PromptService에서 담당하고, 어댑터는 순수한 API 호출만 담당합니다.</p>
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

		// 프롬프트 서비스 상태도 로그 출력
		var promptStatus = promptService.getPromptStatus();
		log.info("프롬프트 설정 - 커스텀 사용: {}, 길이: {}",
			promptStatus.get("customSystemPromptConfigured"),
			promptStatus.get("systemPromptLength"));
	}

	/**
	 * GitLab MR의 변경사항을 분석하여 자동 코드 리뷰를 생성합니다.
	 *
	 * <p>설정된 모델에 따라 자동으로 적절한 LLM 어댑터를 선택하여 리뷰를 생성합니다.
	 * 프롬프트 구성은 PromptService에서 담당하고, 어댑터는 API 호출만 담당합니다.</p>
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 생성된 코드 리뷰 내용을 담은 Mono 객체
	 * @throws LLMException LLM API 호출 중 오류가 발생한 경우
	 */
	public Mono<String> generateReview(List<Diff> diffs) {
		try {
			// 1. 변경사항 유효성 검사
			if (diffs == null || diffs.isEmpty()) {
				return Mono.just(promptService.getNoChangesMessage());
			}

			// 2. 적절한 LLM 어댑터 선택
			LLMAdapter adapter = adapterFactory.getAdapter();

			log.debug("LLM 리뷰 생성 시작 - 어댑터: {}, 파일 수: {}",
				adapter.getClass().getSimpleName(), diffs.size());

			// 3. 어댑터 타입에 따라 프롬프트 처리 방식 결정
			if (adapter instanceof com.inttype.codereview.review.adapter.GeminiAdapter) {
				// Gemini는 시스템/사용자 프롬프트 분리를 지원하지 않으므로 완전한 프롬프트 사용
				String completePrompt = promptService.buildCompletePrompt(diffs);
				return adapter.callApi(completePrompt)
					.doOnSuccess(review -> log.debug("LLM 리뷰 생성 완료 - 길이: {}", review.length()))
					.doOnError(error -> log.error("LLM 리뷰 생성 실패", error));
			} else {
				// OpenAI, Claude는 시스템/사용자 프롬프트 분리 지원
				String systemPrompt = promptService.getSystemPrompt();
				String userPrompt = promptService.getUserPrompt(diffs);

				return adapter.callApi(systemPrompt, userPrompt)
					.doOnSuccess(review -> log.debug("LLM 리뷰 생성 완료 - 길이: {}", review.length()))
					.doOnError(error -> log.error("LLM 리뷰 생성 실패", error));
			}

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

			// 프롬프트 서비스 상태 추가
			var promptStatus = promptService.getPromptStatus();
			status.put("promptService", promptStatus);

		} catch (Exception e) {
			status.put("available", false);
			status.put("error", e.getMessage());
		}

		return status;
	}
}package com.inttype.codereview.review.service;

import java.util.List;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.inttype.codereview.review.adapter.LLMAdapter;
import com.inttype.codereview.review.adapter.LLMAdapterFactory;
import com.inttype.codereview.review.config.LLMProps;
import com.inttype.codereview.review.exception.LLMException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 다양한 LLM을 통한 코드 리뷰 생성 서비스
 *
 * <p>어댑터 패턴을 사용하여 OpenAI, Claude, Gemini 등 다양한 LLM API를
 * 통합된 인터페이스로 제공합니다. 모델명만 변경하면 자동으로 적절한 어댑터가 선택됩니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LLMReviewService {

	private static final String DEFAULT_SYSTEM_PROMPT = """
        당신은 숙련된 코드 리뷰어입니다.
        목표: GitLab MR에 대해 한국어로, 친절하고 명확하며 실행 가능한 리뷰를 작성합니다.
        [톤 & 구조]
        - 첫 문단: 인사 + 요약 칭찬 + 전반 평가 (변경 의도와 결과를 현재 코드 기준으로 서술)
        - 이후: 2~5개의 구체 개선 제안 (근거/이유 포함), 각 항목에 우선순위 [필수/권장/고려] 표기
        - 과한 장황함 금지, 파일/라인/코드 맥락을 구체적으로 언급
        - 프로젝트 컨벤션/성능/보안/테스트/운영 관점 균형
        
        [중요: diff 해석 규칙]
        - unified diff의 `-`는 "과거 코드(제거됨)"를, `+`는 "현재 코드(추가/수정됨)"를 의미합니다.
        - `-`에서 보인 문제를 `+`에서 해결했으면, 이는 "이번 MR에서 해결됨"으로 칭찬/기록합니다.
        - 이미 해결/수정된 사항을 "현재도 남아있는 문제"처럼 오해할 표현은 절대 금지합니다.
        - 개선 제안 섹션에는 "현재 코드 기준으로 추가 조치가 필요한 항목"만 포함합니다.
        - 과거 오타/코드스멜이 이번 MR로 바로잡혔다면 "정정/정리 완료"로 긍정적으로 표현하세요.
        
        [표현 가이드]
        - 첫 문장: "안녕하세요. MR 잘 봤습니다."로 시작
        - 칭찬 1~2개 → 개선 2~5개(우선순위 포함) → (선택) 총평
        - 라인 인용은 파일명:행번호 형태로 간단 표기(가능한 경우)
        - 반드시 사실에만 근거하고 추측/단정 금지
        """;

	private final LLMAdapterFactory adapterFactory;
	private final LLMProps llmProps;

	/**
	 * 서비스 초기화 시 현재 LLM 설정 정보를 로그에 출력합니다.
	 */
	@PostConstruct
	public void initialize() {
		log.info("LLMReviewService 초기화 중...");
		adapterFactory.logCurrentConfiguration();
	}

	/**
	 * GitLab MR의 변경사항을 분석하여 자동 코드 리뷰를 생성합니다.
	 *
	 * <p>설정된 모델에 따라 자동으로 적절한 LLM 어댑터를 선택하여 리뷰를 생성합니다.
	 * 시스템 프롬프트가 설정되지 않은 경우 기본 프롬프트를 사용합니다.</p>
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 생성된 코드 리뷰 내용을 담은 Mono 객체
	 * @throws LLMException LLM API 호출 중 오류가 발생한 경우
	 */
	public Mono<String> generateReview(List<Diff> diffs) {
		try {
			// 1. 적절한 LLM 어댑터 선택
			LLMAdapter adapter = adapterFactory.getAdapter();

			// 2. 시스템 프롬프트 준비
			String systemPrompt = getSystemPrompt();

			// 3. 변경사항 유효성 검사
			if (diffs == null || diffs.isEmpty()) {
				return Mono.just("🤖 변경 사항이 없어 리뷰를 건너뜁니다.");
			}

			log.debug("LLM 리뷰 생성 시작 - 어댑터: {}, 파일 수: {}",
				adapter.getClass().getSimpleName(), diffs.size());

			// 4. 리뷰 생성
			return adapter.generateReview(diffs, systemPrompt)
				.doOnSuccess(review -> log.debug("LLM 리뷰 생성 완료 - 길이: {}", review.length()))
				.doOnError(error -> log.error("LLM 리뷰 생성 실패", error));

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
			status.put("systemPromptConfigured", StringUtils.hasText(llmProps.getSystemPrompt()));
		} catch (Exception e) {
			status.put("available", false);
			status.put("error", e.getMessage());
		}

		return status;
	}

	/**
	 * 시스템 프롬프트를 가져옵니다.
	 * 설정값이 없으면 기본 프롬프트를 사용합니다.
	 *
	 * @return 시스템 프롬프트
	 */
	private String getSystemPrompt() {
		if (StringUtils.hasText(llmProps.getSystemPrompt())) {
			log.debug("설정된 시스템 프롬프트 사용");
			return llmProps.getSystemPrompt();
		}

		log.debug("기본 시스템 프롬프트 사용");
		return DEFAULT_SYSTEM_PROMPT;
	}
}
