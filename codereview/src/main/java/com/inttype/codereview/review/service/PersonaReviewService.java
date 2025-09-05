package com.inttype.codereview.review.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Service;

import com.inttype.codereview.review.adapter.LLMAdapter;
import com.inttype.codereview.review.adapter.LLMAdapterFactory;
import com.inttype.codereview.review.config.PersonaWeightConfig;
import com.inttype.codereview.review.domain.PersonaType;
import com.inttype.codereview.review.exception.LLMException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 페르소나 기반 코드 리뷰 서비스
 *
 * <p>diff 분석을 통해 적절한 페르소나를 선택하고,
 * 해당 페르소나의 전문성에 맞는 코드 리뷰를 생성합니다.
 * 프롬프트 생성은 PromptService에 위임하여 중앙 집중 관리합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PersonaReviewService {

	private final DiffAnalyzer diffAnalyzer;
	private final LLMAdapterFactory adapterFactory;
	private final PromptService promptService;

	/**
	 * GitLab MR의 변경사항을 분석하여 페르소나 기반 코드 리뷰를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 생성된 코드 리뷰 내용을 담은 Mono 객체
	 */
	public Mono<String> generatePersonaReview(List<Diff> diffs) {
		try {
			// 1. diff 분석을 통한 페르소나별 점수 계산
			Map<PersonaType, Integer> scores = diffAnalyzer.analyzePersonaScores(diffs);

			// 2. 최적의 페르소나 선택
			PersonaSelection selection = selectPersona(scores);

			log.info("선택된 페르소나: {}, 점수: {}",
				selection.selectedPersona().getDisplayName(),
				selection.selectedScore());

			// 3. PromptService를 통해 페르소나별 완전한 프롬프트 생성
			String completePrompt = promptService.buildPersonaPrompt(selection.selectedPersona(), diffs);

			// 4. LLM 어댑터를 통한 리뷰 생성
			LLMAdapter adapter = adapterFactory.getAdapter();

			// Gemini처럼 시스템/사용자 프롬프트 분리를 지원하지 않는 API의 경우
			// 완전한 프롬프트를 사용자 프롬프트로 전달하고 시스템 프롬프트는 빈 문자열 사용
			return adapter.generateReview(diffs, completePrompt)
				.map(reviewContent -> formatReviewWithPersona(selection, reviewContent));

		} catch (Exception e) {
			log.error("페르소나 기반 리뷰 생성 중 오류 발생", e);
			return Mono.error(new LLMException(
				LLMException.ErrorType.UNKNOWN,
				"PersonaReview",
				"페르소나 리뷰 생성 실패: " + e.getMessage(),
				e
			));
		}
	}

	/**
	 * 페르소나별 점수를 기반으로 최적의 페르소나를 선택합니다.
	 *
	 * @param scores 페르소나별 점수 맵
	 * @return 선택된 페르소나 정보
	 */
	private PersonaSelection selectPersona(Map<PersonaType, Integer> scores) {
		// 최고 점수 페르소나 찾기
		PersonaType candidatePersona = scores.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(PersonaType.GENERAL_REVIEWER);

		int candidateScore = scores.get(candidatePersona);

		// 임계값 미달 시 기본 페르소나 사용
		final PersonaType selectedPersona;
		final int selectedScore;

		if (candidateScore < PersonaWeightConfig.SELECTION_THRESHOLD) {
			log.info("모든 페르소나 점수가 임계값({}) 미달. 기본 페르소나 사용",
				PersonaWeightConfig.SELECTION_THRESHOLD);
			selectedPersona = PersonaType.GENERAL_REVIEWER;
			selectedScore = scores.get(PersonaType.GENERAL_REVIEWER);
		} else {
			selectedPersona = candidatePersona;
			selectedScore = candidateScore;
		}

		// 추가 점검이 필요한 페르소나들 찾기 (final 변수 사용)
		List<PersonaType> additionalPersonas = scores.entrySet().stream()
			.filter(entry -> entry.getValue() >= PersonaWeightConfig.MENTION_THRESHOLD)
			.filter(entry -> !entry.getKey().equals(selectedPersona))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		return new PersonaSelection(selectedPersona, selectedScore, additionalPersonas);
	}

	/**
	 * 페르소나 정보와 함께 리뷰 내용을 포맷팅합니다.
	 *
	 * @param selection 선택된 페르소나 정보
	 * @param reviewContent LLM이 생성한 리뷰 내용
	 * @return 최종 포맷팅된 리뷰 내용
	 */
	private String formatReviewWithPersona(PersonaSelection selection, String reviewContent) {
		// AI 코드 리뷰임을 명시하는 헤더와 실제 리뷰 내용을 결합
		return String.format("## %s %s AI 코드 리뷰\n\n%s",
			selection.selectedPersona().getEmoji(),
			selection.selectedPersona().getDisplayName(),
			reviewContent);
	}

	/**
	 * 페르소나 선택 결과를 담는 내부 클래스
	 *
	 * @param selectedPersona 선택된 주요 페르소나
	 * @param selectedScore 해당 페르소나의 점수
	 * @param additionalPersonas 추가 점검이 권장되는 페르소나 목록
	 */
	private record PersonaSelection(PersonaType selectedPersona, int selectedScore,
									List<PersonaType> additionalPersonas) {

	}
}
