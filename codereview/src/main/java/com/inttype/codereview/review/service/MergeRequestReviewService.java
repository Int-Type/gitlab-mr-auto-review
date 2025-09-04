package com.inttype.codereview.review.service;

import java.util.List;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MergeRequestReviewService {

	private final GitLabApi gitLabApi;
	private final LLMReviewService llmReviewService;
	private final RetryReviewService retryReviewService;

	@Async // 응답을 빨리 주고 백그라운드로 코멘트 달기
	public void review(Object projectIdOrPath, Long mrIid) {
		try {
			// 1. Get Merge Request Diffs
			MergeRequest mr = gitLabApi.getMergeRequestApi().getMergeRequestChanges(projectIdOrPath, mrIid);
			List<Diff> diffs = mr.getChanges();

			if (diffs == null || diffs.isEmpty()) {
				postComment(projectIdOrPath, mrIid, "🤖 변경 사항이 없어 리뷰를 건너뜁니다.");
				return;
			}

			// 2. Generate Review using LLM API (async)
			llmReviewService.generateReview(diffs)
				.subscribe(
					// onSuccess
					reviewContent -> postComment(projectIdOrPath, mrIid, reviewContent),
					// onError
					error -> {
						log.error("Error generating review for GitLab MR !{}: {}", mrIid, error.getMessage());
						String errorMessage = String.format("🤖 자동 리뷰 중 오류가 발생했습니다: %s", error.getMessage());
						postComment(projectIdOrPath, mrIid, errorMessage);
					}
				);

		} catch (Exception e) {
			log.error("Failed to process review for MR !{}: {}", mrIid, e.getMessage(), e);
			String errorMessage = "자동 리뷰 중 오류가 발생했습니다: " + e.getMessage();
			postComment(projectIdOrPath, mrIid, errorMessage);
		}
	}

	private void postComment(Object projectIdOrPath, Long mrIid, String comment) {
		if (projectIdOrPath instanceof Number) {
			retryReviewService.commentOnMr(((Number) projectIdOrPath).longValue(), mrIid, comment);
		} else {
			retryReviewService.commentOnMr(String.valueOf(projectIdOrPath), mrIid, comment);
		}
	}
}
