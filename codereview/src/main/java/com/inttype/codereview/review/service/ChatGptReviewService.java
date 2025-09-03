package com.inttype.codereview.review.service;

import java.util.List;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.inttype.codereview.review.config.ChatGptProps;
import com.inttype.codereview.review.dto.ChatMessage;
import com.inttype.codereview.review.dto.ChatRequest;
import com.inttype.codereview.review.dto.ChatResponse;

import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChatGptReviewService {

	private static final int MAX_FILE_LIST = 20;
	private static final double DEFAULT_TEMPERATURE = 0.2;
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
		- 첫 문장: “안녕하세요. MR 잘 봤습니다.”로 시작
		- 칭찬 1~2개 → 개선 2~5개(우선순위 포함) → (선택) 총평
		- 라인 인용은 파일명:행번호 형태로 간단 표기(가능한 경우)
		- 반드시 사실에만 근거하고 추측/단정 금지
		""";
	@Qualifier("chatGptWebClient")
	private final WebClient webClient;
	private final ChatGptProps chatGptProps;

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	@Retry(name = "chatgpt")
	public Mono<String> generateReview(List<Diff> diffs) {
		// 1) system prompt: 설정값 없으면 DEFAULT 사용
		// String systemPrompt = isBlank(chatGptProps.getSystemPrompt())
		// 	? DEFAULT_SYSTEM_PROMPT
		// 	: chatGptProps.getSystemPrompt();
		String systemPrompt = DEFAULT_SYSTEM_PROMPT;
		
		// 2) 파일 요약 + diff 본문 생성
		String fileList = (diffs == null || diffs.isEmpty())
			? "- (변경 파일 정보 없음)"
			: diffs.stream()
			.limit(MAX_FILE_LIST)
			.map(d -> "  - " + (d.getNewPath() != null ? d.getNewPath() : d.getOldPath()))
			.collect(Collectors.joining("\n"));

		String userPrompt = """
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
				3) (선택) 총평: 이번 MR로 해결된 사항 요약(예: "그룹명 오타 정정 완료"처럼 ‘이미 반영됨’을 명확히 기재)
			
				[변경사항 unified diff]
				%s
			""".formatted(
			diffs == null ? 0 : diffs.size(),
			MAX_FILE_LIST,
			fileList,
			formatDiffsForPrompt(diffs)
		);

		ChatRequest request = new ChatRequest(
			chatGptProps.getModel(), // <-- getter 사용
			List.of(
				new ChatMessage("system", systemPrompt),
				new ChatMessage("user", userPrompt)
			),
			DEFAULT_TEMPERATURE
		);

		return webClient.post()
			.uri("/chat/completions")
			.bodyValue(request)
			.retrieve()
			.bodyToMono(ChatResponse.class)
			.map(resp -> {
				if (resp == null || resp.getChoices() == null || resp.getChoices().isEmpty()) {
					throw new IllegalStateException("Empty ChatGPT response");
				}
				var msg = resp.getChoices().get(0).getMessage();
				if (msg == null || isBlank(msg.getContent())) {
					throw new IllegalStateException("No content in ChatGPT response");
				}
				return msg.getContent();
			});
	}

	private String formatDiffsForPrompt(List<Diff> diffs) {
		if (diffs == null || diffs.isEmpty())
			return "(diff 없음)";
		return diffs.stream()
			.map(diff -> "File: " + (diff.getNewPath() != null ? diff.getNewPath() : diff.getOldPath())
				+ "\n```diff\n" + (diff.getDiff() == null ? "" : diff.getDiff()) + "\n```")
			.collect(Collectors.joining("\n\n"));
	}
}
