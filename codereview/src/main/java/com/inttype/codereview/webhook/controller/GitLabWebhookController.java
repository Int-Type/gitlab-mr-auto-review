package com.inttype.codereview.webhook.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inttype.codereview.config.GitLabProps;
import com.inttype.codereview.review.service.MergeRequestReviewService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/webhooks/gitlab")
@RequiredArgsConstructor
public class GitLabWebhookController {

	private final GitLabProps props;
	private final MergeRequestReviewService reviewService;

	@PostMapping
	public ResponseEntity<String> onEvent(
		@RequestHeader(value = "X-Gitlab-Token", required = false) String token,
		@RequestHeader(value = "X-Gitlab-Event", required = false) String event,
		@RequestBody Map<String, Object> payload,
		HttpServletRequest request
	) {
		// 0) 이벤트 타입 선검증
		if (!"Merge Request Hook".equalsIgnoreCase(event)) {
			return ResponseEntity.ok("ignored");
		}

		// 1) 토큰 검증
		if (token == null || !token.equals(props.webhookSecret())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid token");
		}

		// 2) MR 이벤트만 처리
		if (!"Merge Request Hook".equalsIgnoreCase(event)) {
			return ResponseEntity.ok("ignored");
		}

		@SuppressWarnings("unchecked")
		Map<String, Object> objectAttributes = (Map<String, Object>)payload.get("object_attributes");
		if (objectAttributes == null)
			return ResponseEntity.badRequest().body("no object_attributes");

		String action = String.valueOf(objectAttributes.get("action")); // opened, reopened, update...
		Number pid = (Number)((Map<String, Object>)payload.get("project")).get("id");
		Number iidNum = (Number)objectAttributes.get("iid");

		Object projectIdOrPath = pid;
		long mrIid = iidNum.longValue();
		
		// 3) 리뷰 트리거 (논블로킹 권장: @Async)
		if ("open".equalsIgnoreCase(action) || "opened".equalsIgnoreCase(action)
			|| "reopen".equalsIgnoreCase(action) || "reopened".equalsIgnoreCase(action)
			|| "update".equalsIgnoreCase(action)) {
			reviewService.review(projectIdOrPath, mrIid);
		}

		return ResponseEntity.ok("ok");
	}
}
