package com.inttype.codereview.review.service;

import org.gitlab4j.api.GitLabApi;
import org.springframework.stereotype.Service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RetryReviewService {

	private final GitLabApi gitLabApi;

	private final CircuitBreaker circuitBreaker =
		CircuitBreaker.ofDefaults("gitlab");
	private final Retry retry = Retry.ofDefaults("gitlab");

	public void commentOnMr(Long projectId, Long mrIid, String comment) {
		Runnable runnable = Retry.decorateRunnable(retry,
			CircuitBreaker.decorateRunnable(circuitBreaker, () -> {
				try {
					gitLabApi.getNotesApi().createMergeRequestNote(projectId, mrIid, comment, null, null);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			})
		);
		runnable.run();
	}

}
