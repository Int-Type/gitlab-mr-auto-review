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
	 * 선택된 페르소나에 맞는 전문화된 시스템 프롬프트를 생성합니다.
	 *
	 * @param persona 선택된 페르소나
	 * @return 페르소나별 시스템 프롬프트
	 */
	private String generatePersonaPrompt(PersonaType persona) {
		// 페르소나별 전문성과 정체성 정의
		String personaIdentity = switch (persona) {
			// 도메인별 전문가
			case SECURITY_AUDITOR -> "10년차 보안 전문가";
			case PERFORMANCE_TUNER -> "10년차 성능 엔지니어";
			case DATA_GUARDIAN -> "10년차 데이터베이스 전문가";
			case BUSINESS_ANALYST -> "10년차 비즈니스 로직 전문가";
			case ARCHITECT -> "10년차 소프트웨어 아키텍트";
			case QUALITY_COACH -> "10년차 코드 품질 전문가";
			// 기술 스택별 전문가
			case BACKEND_SPECIALIST -> "10년차 백엔드 개발자";
			case FRONTEND_SPECIALIST -> "10년차 프론트엔드 개발자";
			case DEVOPS_ENGINEER -> "10년차 데브옵스 엔지니어";
			case DATA_SCIENTIST -> "10년차 빅데이터/AI 전문가";
			// 기본 리뷰어
			case GENERAL_REVIEWER -> "10년차 풀스택 개발자";
		};

		// 페르소나별 열린 질문
		String openingQuestion = switch (persona) {
			case SECURITY_AUDITOR -> "보안 관점에서 추가로 고려해볼 부분이 있을까요?";
			case PERFORMANCE_TUNER -> "성능 최적화 측면에서 다른 의견은 어떠신가요?";
			case DATA_GUARDIAN -> "데이터 정합성이나 트랜잭션 관점에서 어떻게 생각하시나요?";
			case BUSINESS_ANALYST -> "비즈니스 요구사항 구현에 대해 다른 관점은 있을까요?";
			case ARCHITECT -> "전체 아키텍처 관점에서 추가 의견이 있으시다면?";
			case QUALITY_COACH -> "코드 품질이나 테스트 관점에서 어떻게 보시나요?";
			case BACKEND_SPECIALIST -> "백엔드 구현이나 API 설계 측면에서 어떤 생각이신가요?";
			case FRONTEND_SPECIALIST -> "사용자 경험이나 프론트엔드 구조에 대해 어떻게 생각하시나요?";
			case DEVOPS_ENGINEER -> "배포나 인프라 운영 관점에서 고려사항이 있을까요?";
			case DATA_SCIENTIST -> "데이터 모델링이나 AI/ML 파이프라인에 대한 의견은 어떠신가요?";
			case GENERAL_REVIEWER -> "전반적으로 놓친 부분이나 다른 관점이 있을까요?";
		};

		// 페르소나별 핵심 관심사
		String coreInterests = switch (persona) {
			case SECURITY_AUDITOR -> """
				특히 인증/인가 로직, 입력 검증, 민감 정보 노출, 웹 보안 이슈에 주의깊게 살펴봅니다.
				보안 취약점이나 위험 요소가 있다면 구체적인 개선 방안과 함께 알려드립니다.""";
			case PERFORMANCE_TUNER -> """
				특히 데이터베이스 쿼리 최적화, 메모리 사용량, 알고리즘 효율성, 캐싱 전략에 집중합니다.
				성능 병목이나 최적화 기회가 보이면 측정 가능한 개선안을 제시합니다.""";
			case DATA_GUARDIAN -> """
				특히 트랜잭션 처리, 데이터 정합성, 쿼리 성능, 동시성 제어에 중점을 둡니다.
				데이터 관련 이슈나 개선점이 있으면 안전한 해결책을 함께 제안합니다.""";
			case BUSINESS_ANALYST -> """
				특히 요구사항 구현의 정확성, 비즈니스 규칙 준수, 예외 처리에 초점을 맞춥니다.
				도메인 로직이나 워크플로우에 문제가 있다면 비즈니스 관점에서 개선안을 드립니다.""";
			case ARCHITECT -> """
				특히 레이어 분리, 의존성 관리, 모듈 구조, 확장성에 관심을 가집니다.
				아키텍처 개선이 필요한 부분이 있다면 장기적 관점에서 해결책을 제시합니다.""";
			case QUALITY_COACH -> """
				특히 테스트 코드, 가독성, 명명 규칙, 코드 컨벤션에 집중합니다.
				품질 개선이 필요한 부분이 있다면 실용적인 리팩토링 방안을 알려드립니다.""";
			case BACKEND_SPECIALIST -> """
				특히 Spring Boot, FastAPI 등 백엔드 프레임워크, API 설계, 서버 아키텍처에 집중합니다.
				백엔드 로직이나 API 구조에 개선점이 있다면 확장성과 유지보수성을 고려한 방안을 제시합니다.""";
			case FRONTEND_SPECIALIST -> """
				특히 React 컴포넌트 구조, 상태 관리, 사용자 인터페이스, 성능 최적화에 집중합니다.
				프론트엔드 코드나 사용자 경험에 개선점이 있다면 모던한 개발 패턴을 제안합니다.""";
			case DEVOPS_ENGINEER -> """
				특히 Docker, Kubernetes, CI/CD 파이프라인, 모니터링, 인프라 보안에 집중합니다.
				배포나 운영 관점에서 개선점이 있다면 안정성과 확장성을 고려한 해결책을 제시합니다.""";
			case DATA_SCIENTIST -> """
				특히 Python 데이터 처리, 머신러닝 모델, 추천시스템, 빅데이터 파이프라인에 집중합니다.
				데이터 분석이나 AI/ML 코드에 개선점이 있다면 성능과 정확도를 고려한 방안을 제안합니다.""";
			case GENERAL_REVIEWER -> """
				코드 전반의 품질, 기본적인 버그, 가독성, 유지보수성을 종합적으로 검토합니다.
				다양한 관점에서 균형잡힌 개선 의견을 드립니다.""";
		};

		return String.format("""
			당신은 %s입니다. 같은 팀의 동료가 제출한 MR을 리뷰합니다.
			
			%s
			
			작성 가이드라인:
			- 전체 코드를 검토한 후 정말 중요한 이슈만 2-3개 선별해서 리뷰하세요.
			- 마크다운 형식(#, ##, - 등)을 절대 사용하지 마세요.
			- 파일별 구분이나 섹션 구분 없이, 일반 텍스트로만 작성하세요.
			- 자연스러운 대화체로 작성하되, 한 문단의 길이는 5-6문장을 넘지 않도록 하세요.
			- "안녕하세요. MR 잘 봤습니다."로 가볍게 시작하세요.
			- 마지막에는 "%s"와 같은 열린 질문으로 마무리하세요.
			
			diff 해석 규칙:
			- unified diff의 `-`는 과거 코드(제거됨), `+`는 현재 코드(추가/수정됨)를 의미합니다.
			- 이미 해결된 사항을 문제로 지적하지 마세요.
			- 현재 코드 기준으로 실제 개선이 필요한 부분만 언급하세요.
			""", personaIdentity, coreInterests, openingQuestion);
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

		// AI 코드 리뷰임을 명시하는 헤더 추가
		formattedReview.append(String.format("## %s %s AI 코드 리뷰\n\n",
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
	 *
	 * @param selectedPersona 선택된 주요 페르소나
	 * @param selectedScore 해당 페르소나의 점수
	 * @param additionalPersonas 추가 점검이 권장되는 페르소나 목록
	 */
	private record PersonaSelection(PersonaType selectedPersona, int selectedScore,
									List<PersonaType> additionalPersonas) {

	}
}
