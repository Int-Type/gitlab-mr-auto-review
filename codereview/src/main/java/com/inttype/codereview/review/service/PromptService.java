package com.inttype.codereview.review.service;

import java.util.List;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.jvnet.hk2.annotations.Service;
import org.springframework.util.StringUtils;

import com.inttype.codereview.review.config.LLMProps;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 코드 리뷰용 프롬프트 생성 및 관리 전담 서비스
 * 시스템 프롬프트와 사용자 프롬프트를 통합 관리하여
 * 일관된 리뷰 품질을 보장하고 프롬프트 변경을 중앙화합니다.
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
	 * 기본 시스템 프롬프트
	 * 모든 LLM에서 공통으로 사용되는 리뷰 가이드라인
	 */
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

	private final LLMProps llmProps;

	/**
	 * GitLab MR 변경사항을 기반으로 완전한 프롬프트를 생성합니다.
	 *
	 * <p>시스템 프롬프트와 변경사항 기반 사용자 프롬프트를 결합하여
	 * LLM이 바로 처리할 수 있는 완전한 프롬프트를 반환합니다.</p>
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 완성된 프롬프트 (시스템 + 사용자 프롬프트 결합)
	 */
	public String buildCompletePrompt(List<Diff> diffs) {
		String systemPrompt = getSystemPrompt();
		String userPrompt = buildUserPrompt(diffs);

		log.debug("프롬프트 생성 완료 - 파일 수: {}, 시스템 프롬프트 길이: {}",
			diffs != null ? diffs.size() : 0, systemPrompt.length());

		return combinePrompts(systemPrompt, userPrompt);
	}

	/**
	 * 시스템 프롬프트만 반환합니다.
	 * (OpenAI처럼 시스템/사용자 프롬프트를 분리해서 전송하는 API용)
	 *
	 * @return 시스템 프롬프트
	 */
	public String getSystemPrompt() {
		if (StringUtils.hasText(llmProps.getSystemPrompt())) {
			log.debug("설정된 커스텀 시스템 프롬프트 사용");
			return llmProps.getSystemPrompt();
		}

		log.debug("기본 시스템 프롬프트 사용");
		return DEFAULT_SYSTEM_PROMPT;
	}

	/**
	 * 사용자 프롬프트만 반환합니다.
	 * (OpenAI처럼 시스템/사용자 프롬프트를 분리해서 전송하는 API용)
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 사용자 프롬프트
	 */
	public String getUserPrompt(List<Diff> diffs) {
		return buildUserPrompt(diffs);
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
	 * (디버깅 및 상태 확인용)
	 *
	 * @return 프롬프트 설정 정보
	 */
	public java.util.Map<String, Object> getPromptStatus() {
		java.util.Map<String, Object> status = new java.util.HashMap<>();

		status.put("customSystemPromptConfigured", StringUtils.hasText(llmProps.getSystemPrompt()));
		status.put("systemPromptLength", getSystemPrompt().length());
		status.put("defaultSystemPromptUsed", !StringUtils.hasText(llmProps.getSystemPrompt()));

		return status;
	}

	/**
	 * diff 정보를 기반으로 사용자 프롬프트를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 생성된 사용자 프롬프트
	 */
	private String buildUserPrompt(List<Diff> diffs) {
		String fileList = generateFileList(diffs);
		String diffContent = formatDiffsForPrompt(diffs);

		return String.format("""
            다음 변경사항에 대해 위의 규칙을 지켜 리뷰를 작성하세요.
            
            [컨텍스트]
            - 변경 파일 수: %d
            - 변경 파일 목록(상위 %d개까지 표시):
            %s
            
            [diff 해석 규칙(엄수)]
            - unified diff의 `-`는 과거 상태(제거된 코드), `+`는 현재 상태(이번 MR 반영 후)입니다.
            - `-`에서 문제가 있었고 `+`에서 해결된 경우, "이번 MR에서 해결됨/정리됨"으로 칭찬·요약만 하세요.
            - 이미 해결된 항목을 개선 제안에 넣지 마세요. 개선 제안에는 "현재 기준으로 남은 액션"만 포함하세요.
            - 남은 리스크/테스트 보완/컨벤션/보안·성능 관점에서의 추가 조치가 있다면 구체적으로 제시하세요.
            
            [리뷰 출력 포맷(엄수)]
            1) 인사 + 요약 칭찬/전반 평가 (2~4문장, 현재 상태 기준으로 작성)
            2) 개선 제안 (2~5개, 각 항목에 우선순위 [필수/권장/고려] 표기)
               - 형식: [우선순위] 파일명:행번호 - 한 줄 제목
                 근거/이유 (1~2문장, 현재 코드 기준)
                 간단한 대안/예시 (필요 시 1~5줄 코드블록)
            3) (선택) 총평: 이번 MR로 해결된 사항 요약(예: "그룹명 오타 정정 완료"처럼 '이미 반영됨'을 명확히 기재)
            
            [변경사항 unified diff]
            %s
            """,
			diffs == null ? 0 : diffs.size(),
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
		if (diffs == null || diffs.isEmpty()) {
			return "- (변경 파일 정보 없음)";
		}

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
		if (diffs == null || diffs.isEmpty()) {
			return "(diff 없음)";
		}

		return diffs.stream()
			.map(diff -> "File: " + (diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath())
				+ "\n```diff\n" + (diff.getDiff() == null ? "" : diff.getDiff()) + "\n```")
			.collect(Collectors.joining("\n\n"));
	}

	/**
	 * 시스템 프롬프트와 사용자 프롬프트를 결합합니다.
	 * (Gemini 같이 분리된 프롬프트를 지원하지 않는 API용)
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

}
