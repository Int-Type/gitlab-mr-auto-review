package com.inttype.codereview.review.service;

import java.util.List;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Service;

import com.inttype.codereview.review.config.ReviewModeConfig;
import com.inttype.codereview.review.domain.PersonaType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 코드 리뷰용 프롬프트 생성 및 관리 전담 서비스
 *
 * <p>기본 베이스 프롬프트 + 페르소나별 전문성 + diff 내용을 조합하여
 * 완전한 프롬프트를 생성합니다. 모든 프롬프트는 하드코딩으로 관리됩니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

	private static final int MAX_FILE_LIST = 20;
	/**
	 * 모든 리뷰에서 공통으로 사용되는 기본 베이스 프롬프트
	 * diff 해석 규칙, 리뷰 포맷, 작성 가이드라인 등 공통 규칙 포함
	 */
	private static final String BASE_PROMPT = """
		[diff 해석 규칙 (필수)]
		- unified diff의 `-`는 "과거 코드(제거됨)"를, `+`는 "현재 코드(추가/수정됨)"를 의미합니다.
		- 이미 해결/수정된 사항을 "현재도 남아있는 문제"처럼 오해할 표현은 절대 금지합니다.
		- 개선 제안 섹션에는 "현재 코드 기준으로 추가 조치가 필요한 항목"만 포함합니다.
		
		[작성 가이드라인]
		- 전체 코드를 검토한 후 정말 중요한 이슈만 2-3개 선별해서 리뷰하세요.
		- 마크다운 형식(#, ##, - 등)을 절대 사용하지 마세요.
		- 파일별 구분이나 섹션 구분 없이, 일반 텍스트로만 작성하세요.
		- 자연스러운 대화체로 작성하되, 한 문단의 길이는 5-6문장을 넘지 않도록 하세요.
		- "안녕하세요. MR 잘 봤습니다."로 가볍게 시작하세요.
		- 반드시 사실에만 근거하고 추측/단정 금지
		
		""";
	private final ReviewModeConfig reviewModeConfig;

	/**
	 * 통합 LLM 모드용 완전한 프롬프트를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 완성된 프롬프트
	 */
	public String buildIntegratedPrompt(List<Diff> diffs) {
		log.debug("통합 모드 프롬프트 생성 - 파일 수: {}", diffs != null ? diffs.size() : 0);

		String generalReviewerIdentity = """
			당신은 10년차 풀스택 개발자입니다. 
			코드 전반의 품질, 기본적인 버그, 가독성, 유지보수성을 종합적으로 검토합니다.
			다양한 관점에서 균형잡힌 개선 의견을 드립니다.
			
			마지막에는 "전반적으로 놓친 부분이나 다른 관점이 있을까요?"와 같은 열린 질문으로 마무리하세요.
			""";

		return combinePrompt(generalReviewerIdentity, diffs);
	}

	/**
	 * 페르소나별 완전한 프롬프트를 생성합니다.
	 *
	 * @param persona 선택된 페르소나 타입
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 페르소나별 완성된 프롬프트
	 */
	public String buildPersonaPrompt(PersonaType persona, List<Diff> diffs) {
		log.debug("페르소나 프롬프트 생성 - 페르소나: {}, 파일 수: {}",
			persona.getDisplayName(), diffs != null ? diffs.size() : 0);

		String personaSpecificContent = getPersonaSpecificContent(persona);
		return combinePrompt(personaSpecificContent, diffs);
	}

	/**
	 * 변경사항이 없을 때 반환할 메시지를 생성합니다.
	 *
	 * @return 변경사항 없음 메시지
	 */
	public String getNoChangesMessage() {
		return "🤖 변경 사항이 없어 리뷰를 건너뜁니다.";
	}

	/**
	 * 현재 프롬프트 설정 상태를 반환합니다.
	 *
	 * @return 프롬프트 설정 정보 맵
	 */
	public java.util.Map<String, Object> getPromptStatus() {
		java.util.Map<String, Object> status = new java.util.HashMap<>();

		status.put("reviewMode", reviewModeConfig.getMode());
		status.put("isPersonaMode", reviewModeConfig.isPersonaMode());
		status.put("isIntegratedMode", reviewModeConfig.isIntegratedMode());
		status.put("promptSource", "hardcoded");
		status.put("basePromptLength", BASE_PROMPT.length());

		return status;
	}

	/**
	 * 페르소나별 전문성 내용을 반환합니다.
	 *
	 * @param persona 페르소나 타입
	 * @return 페르소나별 전문성 내용
	 */
	private String getPersonaSpecificContent(PersonaType persona) {
		// 페르소나별 정체성
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

		// 페르소나별 마무리 질문
		String closingQuestion = switch (persona) {
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

		return String.format("""
			당신은 %s입니다. 같은 팀의 동료가 제출한 MR을 리뷰합니다.
			
			%s
			
			마지막에는 "%s"와 같은 열린 질문으로 마무리하세요.
			""", personaIdentity, coreInterests, closingQuestion);
	}

	/**
	 * 베이스 프롬프트 + 페르소나별 내용 + diff를 조합하여 완전한 프롬프트를 생성합니다.
	 *
	 * @param personaContent 페르소나별 전문성 내용
	 * @param diffs 변경사항 목록
	 * @return 완성된 프롬프트
	 */
	private String combinePrompt(String personaContent, List<Diff> diffs) {
		if (diffs == null || diffs.isEmpty()) {
			return getNoChangesMessage();
		}

		String fileList = generateFileList(diffs);
		String diffContent = formatDiffsForPrompt(diffs);

		return String.format("""
				%s
				
				%s
				
				다음 변경사항에 대해 위의 규칙을 지켜 리뷰를 작성하세요.
				
				[컨텍스트]
				- 변경 파일 수: %d
				- 변경 파일 목록(상위 %d개까지 표시):
				%s
				
				[변경사항]
				%s
				""",
			BASE_PROMPT,
			personaContent,
			diffs.size(),
			MAX_FILE_LIST,
			fileList,
			diffContent
		);
	}

	/**
	 * 변경된 파일 목록을 생성합니다.
	 *
	 * @param diffs 변경사항 목록
	 * @return 포맷된 파일 목록 문자열
	 */
	private String generateFileList(List<Diff> diffs) {
		return diffs.stream()
			.limit(MAX_FILE_LIST)
			.map(d -> "  - " + (d.getNewPath() != null ? d.getNewPath() : d.getOldPath()))
			.collect(Collectors.joining("\n"));
	}

	/**
	 * diff 정보를 프롬프트 형식으로 포맷합니다.
	 *
	 * @param diffs 변경사항 목록
	 * @return 포맷된 diff 문자열
	 */
	private String formatDiffsForPrompt(List<Diff> diffs) {
		return diffs.stream()
			.map(diff -> "File: " + (diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath())
				+ "\n```diff\n" + (diff.getDiff() == null ? "" : diff.getDiff()) + "\n```")
			.collect(Collectors.joining("\n\n"));
	}
}
