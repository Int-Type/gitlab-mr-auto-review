package com.inttype.codereview.review.service;

import java.util.List;

import org.gitlab4j.api.GitLabApi;
import org.gitlab4j.api.models.Diff;
import org.gitlab4j.api.models.MergeRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.inttype.codereview.review.config.ReviewModeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * GitLab Merge Request 코드 리뷰 서비스
 *
 * <p>GitLab MR 이벤트를 받아 페르소나 기반 자동 코드 리뷰를 생성하고
 * MR에 댓글로 게시하는 서비스입니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MergeRequestReviewService {

	private final GitLabApi gitLabApi;
	private final LLMReviewService llmReviewService;
	private final PersonaReviewService personaReviewService;
	private final RetryReviewService retryReviewService;
	private final ReviewModeConfig reviewModeConfig;

	/**
	 * MR에 대한 비동기 코드 리뷰를 수행합니다.
	 *
	 * <p>MR의 변경사항을 분석하여 적절한 페르소나를 선택하고,
	 * 해당 전문가 관점에서 코드 리뷰를 생성합니다.</p>
	 *
	 * @param projectIdOrPath GitLab 프로젝트 ID 또는 경로
	 * @param mrIid MR의 내부 ID
	 */
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

			log.info("MR !{} 리뷰 시작 - 변경 파일 수: {}, 리뷰 모드: {}",
				mrIid, diffs.size(), reviewModeConfig.getMode());

			// 2. 리뷰 모드에 따라 적절한 서비스 선택
			if (reviewModeConfig.isPersonaMode()) {
				// 페르소나 기반 리뷰 생성
				personaReviewService.generatePersonaReview(diffs)
					.subscribe(
						// onSuccess
						reviewContent -> {
							log.info("페르소나 기반 리뷰 생성 완료 - MR !{}", mrIid);
							postComment(projectIdOrPath, mrIid, reviewContent);
						},
						// onError
						error -> {
							log.error("페르소나 기반 리뷰 생성 실패 - MR !{}: {}", mrIid, error.getMessage());
							handleReviewError(projectIdOrPath, mrIid, error, "페르소나 기반");
						}
					);
			} else {
				// 기존 통합 LLM 리뷰 생성
				llmReviewService.generateReview(diffs)
					.subscribe(
						// onSuccess
						reviewContent -> {
							log.info("통합 LLM 리뷰 생성 완료 - MR !{}", mrIid);
							postComment(projectIdOrPath, mrIid, reviewContent);
						},
						// onError
						error -> {
							log.error("통합 LLM 리뷰 생성 실패 - MR !{}: {}", mrIid, error.getMessage());
							handleReviewError(projectIdOrPath, mrIid, error, "통합 LLM");
						}
					);
			}

		} catch (Exception e) {
			log.error("MR !{} 리뷰 처리 중 예상치 못한 오류: {}", mrIid, e.getMessage(), e);
			String errorMessage = "자동 리뷰 중 오류가 발생했습니다: " + e.getMessage();
			postComment(projectIdOrPath, mrIid, errorMessage);
		}
	}

	/**
	 * 리뷰 생성 오류를 처리하고 적절한 에러 메시지를 게시합니다.
	 *
	 * @param projectIdOrPath GitLab 프로젝트 식별자
	 * @param mrIid MR 내부 ID
	 * @param error 발생한 오류
	 * @param reviewType 리뷰 유형 (로깅용)
	 */
	private void handleReviewError(Object projectIdOrPath, Long mrIid, Throwable error, String reviewType) {
		String errorMessage;

		if (error.getMessage().contains("API 키")) {
			errorMessage = "🤖 LLM API 설정을 확인해주세요. 관리자에게 문의하시기 바랍니다.";
		} else if (error.getMessage().contains("요청 한도")) {
			errorMessage = "🤖 API 요청 한도를 초과했습니다. 잠시 후 다시 시도하거나 관리자에게 문의하세요.";
		} else if (error.getMessage().contains("네트워크")) {
			errorMessage = "🤖 네트워크 연결 문제로 리뷰 생성에 실패했습니다. 잠시 후 다시 시도하세요.";
		} else {
			errorMessage = String.format("🤖 %s 자동 리뷰 중 오류가 발생했습니다: %s", reviewType, error.getMessage());
		}

		postComment(projectIdOrPath, mrIid, errorMessage);
	}

	/**
	 * MR에 댓글을 게시합니다.
	 *
	 * @param projectIdOrPath GitLab 프로젝트 식별자
	 * @param mrIid MR 내부 ID
	 * @param comment 게시할 댓글 내용
	 */
	private void postComment(Object projectIdOrPath, Long mrIid, String comment) {
		try {
			if (projectIdOrPath instanceof Number) {
				retryReviewService.commentOnMr(((Number)projectIdOrPath).longValue(), mrIid, comment);
			} else {
				retryReviewService.commentOnMr(String.valueOf(projectIdOrPath), mrIid, comment);
			}
			log.debug("MR !{} 댓글 게시 완료", mrIid);
		} catch (Exception e) {
			log.error("MR !{} 댓글 게시 실패: {}", mrIid, e.getMessage(), e);
		}
	}
}
