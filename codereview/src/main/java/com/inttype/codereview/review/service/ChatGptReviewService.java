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
		스타일:
		- 첫 문단에서 요약 칭찬 + 전반 평가
		- 이후 2~5개의 구체 개선 제안(근거/이유 포함)
		- 제안의 우선순위(필수/권장/고려) 표시
		- 과한 장황함 금지, 코드/파일명을 구체적으로 언급
		- 프로젝트 컨벤션/성능/보안/UX를 균형 있게 다룸
		제약:
		- 주어진 변경사항과 컨텍스트에만 근거(추측 지양)
		- 라인 인용은 파일명:행번호 형태로 간단 표기
		
		[리뷰 톤/스멜 가이드]
		- 첫 문장은 “안녕하세요. MR 잘 봤습니다.”로 시작
		- 칭찬 1~2개 → 개선 3~5개(우선순위 포함)
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
		String systemPrompt = isBlank(chatGptProps.getSystemPrompt())
			? DEFAULT_SYSTEM_PROMPT
			: chatGptProps.getSystemPrompt();

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
			
			[리뷰 출력 포맷(엄수)]
			1) 인사 + 요약 칭찬/전반 평가 (2~4문장)
			2) 개선 제안 (2~5개, 각 항목에 우선순위 [필수/권장/고려] 표기)
			   - 형식: [우선순위] 파일명:행번호 - 한 줄 제목
			     근거/이유 (1~2문장)
			     간단한 대안/예시 (필요 시 1~5줄 코드블록)
			3) 총평 (선택)
			
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
