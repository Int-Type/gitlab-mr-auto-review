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
 * 해당 페르소나의 전문성에 맞는 코드 리뷰를 생성합니다.</p>
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

			// 3. 선택된 페르소나에 맞는 시스템 프롬프트 생성
			String systemPrompt = generatePersonaPrompt(selection.selectedPersona());

			// 4. LLM 어댑터를 통한 리뷰 생성
			LLMAdapter adapter = adapterFactory.getAdapter();
			return adapter.generateReview(diffs, systemPrompt)
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
		PersonaType selectedPersona = scores.entrySet().stream()
			.max(Map.Entry.comparingByValue())
			.map(Map.Entry::getKey)
			.orElse(PersonaType.GENERAL_REVIEWER);

		int selectedScore = scores.get(selectedPersona);

		// 임계값 미달 시 기본 페르소나 사용
		if (selectedScore < PersonaWeightConfig.SELECTION_THRESHOLD) {
			log.info("모든 페르소나 점수가 임계값({}) 미달. 기본 페르소나 사용",
				PersonaWeightConfig.SELECTION_THRESHOLD);
			selectedPersona = PersonaType.GENERAL_REVIEWER;
			selectedScore = scores.get(PersonaType.GENERAL_REVIEWER);
		}

		// 추가 점검이 필요한 페르소나들 찾기
		List<PersonaType> additionalPersonas = scores.entrySet().stream()
			.filter(entry -> entry.getValue() >= PersonaWeightConfig.MENTION_THRESHOLD)
			.filter(entry -> !entry.getKey().equals(selectedPersona))
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		return new PersonaSelection(selectedPersona, selectedScore, additionalPersonas);
	}

	/**
	 * 선택된 페르소나에 맞는 전문화된 시스템 프롬프트를 생성합니다.
	 *
	 * @param persona 선택된 페르소나
	 * @return 페르소나별 시스템 프롬프트
	 */
	private String generatePersonaPrompt(PersonaType persona) {
		String basePrompt = """
			당신은 숙련된 코드 리뷰어입니다.
			목표: GitLab MR에 대해 한국어로, 친절하고 명확하며 실행 가능한 리뷰를 작성합니다.
			
			[기본 구조]
			- 첫 문단: "%s 안녕하세요. MR 잘 봤습니다." + 요약 칭찬 + 전반 평가
			- 이후: 2~5개의 구체 개선 제안 (우선순위 [필수/권장/고려] 표기)
			- 파일/라인 단위로 구체적 언급, 근거와 대안 제시
			
			[diff 해석 규칙]
			- unified diff의 `-`는 과거 코드(제거됨), `+`는 현재 코드(추가/수정됨)
			- 이미 해결된 사항을 문제로 지적하지 마세요
			- 개선 제안에는 현재 코드 기준으로 추가 조치가 필요한 항목만 포함
			""";

		String personaSpecific = switch (persona) {
			case SECURITY_AUDITOR -> """
				
				[보안 전문가로서 중점 검토 사항]
				- 인증/인가 로직의 적절성
				- 입력 검증 및 SQL 인젝션 방지
				- 민감 정보 노출 위험성
				- CORS, CSRF 등 웹 보안 이슈
				- API 엔드포인트 보안 설정
				""";

			case PERFORMANCE_TUNER -> """
				
				[성능 전문가로서 중점 검토 사항]
				- N+1 쿼리 및 데이터베이스 최적화
				- 메모리 사용량 및 GC 영향
				- 불필요한 반복문이나 비효율적 알고리즘
				- 캐싱 전략 및 비동기 처리 기회
				- 대용량 데이터 처리 최적화
				""";

			case DATA_GUARDIAN -> """
				
				[데이터 전문가로서 중점 검토 사항]
				- 트랜잭션 경계 및 격리 수준
				- 데이터 정합성 및 제약 조건
				- 쿼리 성능 및 인덱스 활용
				- 데이터 마이그레이션 안전성
				- 동시성 제어 및 락 전략
				""";

			case BUSINESS_ANALYST -> """
				
				[비즈니스 로직 전문가로서 중점 검토 사항]
				- 요구사항 구현의 정확성
				- 비즈니스 규칙 및 도메인 로직 검증
				- 예외 상황 및 엣지 케이스 처리
				- 워크플로우의 논리적 일관성
				- 도메인 용어 및 명명 규칙 준수
				""";

			case ARCHITECT -> """
				
				[아키텍처 전문가로서 중점 검토 사항]
				- 레이어 분리 및 의존성 방향
				- 인터페이스 설계 및 추상화 수준
				- 모듈 간 결합도 및 응집도
				- 확장성 및 유지보수성 고려
				- 디자인 패턴 적용의 적절성
				""";

			case QUALITY_COACH -> """
				
				[품질 관리 전문가로서 중점 검토 사항]
				- 테스트 코드의 적절성 및 커버리지
				- 코드 가독성 및 명명 규칙
				- 주석 및 문서화 품질
				- 코드 컨벤션 준수
				- 리팩토링 기회 및 코드 냄새 제거
				""";

			case GENERAL_REVIEWER -> """
				
				[종합 검토자로서 중점 검토 사항]
				- 전반적인 코드 품질 및 구조
				- 기본적인 버그 및 논리 오류
				- 간단한 성능 개선 기회
				- 가독성 및 유지보수성
				- 일반적인 모범 사례 준수
				""";
		};

		return String.format(basePrompt, persona.getEmoji()) + personaSpecific;
	}

	/**
	 * 페르소나 정보와 함께 리뷰 내용을 포맷팅합니다.
	 *
	 * @param selection 선택된 페르소나 정보
	 * @param reviewContent LLM이 생성한 리뷰 내용
	 * @return 최종 포맷팅된 리뷰 내용
	 */
	private String formatReviewWithPersona(PersonaSelection selection, String reviewContent) {
		StringBuilder formattedReview = new StringBuilder();

		// 페르소나 정보 헤더 추가
		formattedReview.append(String.format("## %s %s 리뷰\n",
			selection.selectedPersona().getEmoji(),
			selection.selectedPersona().getDisplayName()));

		// 실제 리뷰 내용
		formattedReview.append(reviewContent);

		// 추가 점검 필요 페르소나가 있는 경우 멘트 추가
		if (!selection.additionalPersonas().isEmpty()) {
			formattedReview.append("\n\n---\n");
			formattedReview.append("**추가 점검 권장 영역**: ");

			String additionalMentions = selection.additionalPersonas().stream()
				.map(persona -> String.format("%s %s", persona.getEmoji(), persona.getDisplayName()))
				.collect(Collectors.joining(", "));

			formattedReview.append(additionalMentions);
			formattedReview.append(" 관점에서도 검토해보시기 바랍니다.");
		}

		return formattedReview.toString();
	}

	/**
	 * 페르소나 선택 결과를 담는 내부 클래스
	 */
	private record PersonaSelection(PersonaType selectedPersona, int selectedScore,
									List<PersonaType> additionalPersonas) {

	}
}
