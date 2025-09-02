package com.inttype.codereview.review.service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MergeRequestReviewService {

	private static final Pattern SECRET_PATTERN =
		Pattern.compile("(api[-_ ]?key|secret|password)\\s*[:=]\\s*['\\\"][A-Za-z0-9_.-]{12,}['\\\"]",
			Pattern.CASE_INSENSITIVE);
	private final GitLabApi gitLabApi;
	private final RetryReviewService retryReviewService;

	@Async // 응답을 빨리 주고 백그라운드로 코멘트 달기
	public void review(Object projectIdOrPath, Long mrIid) {
		try {
			var mrApi = gitLabApi.getMergeRequestApi();

			// 변경 파일 가져오기
			MergeRequest mr = mrApi.getMergeRequestChanges(projectIdOrPath, mrIid);

			List<Diff> diffs = mr.getChanges(); // 각 파일의 diff가 들어있음

			StringBuilder report = new StringBuilder("### 🤖 자동 코드 리뷰 요약\n");
			int largeFiles = 0, todoCount = 0, printlnCount = 0, secretHits = 0;

			for (Diff d : diffs) {
				String file = d.getNewPath() != null ? d.getNewPath() : d.getOldPath();
				String patch = d.getDiff() == null ? "" : d.getDiff();

				// 간단 규칙들
				if (patch.length() > 20000)
					largeFiles++;
				if (patch.matches("(?s).*TODO.*")) {
					todoCount++;
				}
				if (patch.contains("System.out.println") || patch.contains("printStackTrace(")) {
					printlnCount++;
				}
				if (SECRET_PATTERN.matcher(patch).find()) {
					secretHits++;
				}

				// 테스트 미포함 예시
				if (!file.contains("test") && (file.endsWith(".java") || file.endsWith(".kt"))) {
					// 필요하면 더 정교하게
				}
			}

			report.append("- 변경 파일 수: ").append(diffs.size()).append("\n");
			if (largeFiles > 0)
				report.append("- ⚠️ 매우 큰 변경 패치: ").append(largeFiles).append("개\n");
			if (todoCount > 0)
				report.append("- 📝 TODO가 남아있어요: ").append(todoCount).append("곳\n");
			if (printlnCount > 0)
				report.append("- 🔇 로그/디버그 출력이 포함됨: ")
					.append(printlnCount)
					.append("곳 (`System.out.println`, `printStackTrace`)\n");
			if (secretHits > 0)
				report.append("- 🔐 잠재적 시크릿 노출 패턴 감지: ").append(secretHits).append("곳 (검토 필요)\n");

			// 변경 파일 리스트
			report.append("\n#### 변경 파일\n");
			report.append(diffs.stream()
				.map(d -> "- " + (d.getNewPath() != null ? d.getNewPath() : d.getOldPath()))
				.collect(Collectors.joining("\n")));

			// MR에 코멘트
			addMrNote(projectIdOrPath, mrIid, report.toString());

		} catch (Exception e) {
			try {
				addMrNote(projectIdOrPath, mrIid, "자동 리뷰 중 오류: " + e.getMessage());
			} catch (Exception ignore) {
			}
		}
	}

	private void addMrNote(Object projectIdOrPath, Long mrIid, String body) throws org.gitlab4j.api.GitLabApiException {
		gitLabApi.getNotesApi().createMergeRequestNote(projectIdOrPath, mrIid, body, null, null);
	}
}
