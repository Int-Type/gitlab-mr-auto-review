package com.inttype.codereview.webhook.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inttype.codereview.review.config.GitLabProps;
import com.inttype.codereview.review.service.MergeRequestReviewService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
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
		@SuppressWarnings("unchecked")
		Map<String, Object> objectAttributes = (Map<String, Object>)payload.get("object_attributes");

		@SuppressWarnings("unchecked")
		Map<String, Object> project = (Map<String, Object>)payload.get("project");

		// 값 꺼내기 (널 안전)
		String action = objectAttributes != null ? String.valueOf(objectAttributes.get("action")) : null;
		Object projId = project != null ? project.get("id") : null;
		Object pathWithNs = project != null ? project.get("path_with_namespace") : null;
		Object iidObj = objectAttributes != null ? objectAttributes.get("iid") : null;
		long mrIid = (iidObj instanceof Number) ? ((Number)iidObj).longValue() : -1L;

		log.info("Webhook: event={}, action={}, projId={}, path={}, iid={}",
			event, action, projId, pathWithNs, mrIid);

		// 1) 토큰 검증
		if (token == null || !token.equals(props.getWebhookSecret())) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid token");
		}

		// 2) MR 이벤트만 처리
		if (!"Merge Request Hook".equalsIgnoreCase(event)) {
			return ResponseEntity.ok("ignored");
		}

		if (objectAttributes == null)
			return ResponseEntity.badRequest().body("no object_attributes");

		if (!(projId instanceof Number) || !(iidObj instanceof Number)) {
			return ResponseEntity.badRequest().body("invalid project id or iid");
		}

		// 3) 리뷰 트리거 (논블로킹 권장: @Async)
		if ("open".equalsIgnoreCase(action) || "opened".equalsIgnoreCase(action)
			|| "reopen".equalsIgnoreCase(action) || "reopened".equalsIgnoreCase(action)
			|| "update".equalsIgnoreCase(action) || "updated".equalsIgnoreCase(action)) {

			// projectIdOrPath는 id(Number) 또는 path_with_namespace(String) 사용 가능
			// 여기서는 id를 우선 사용
			Object projectIdOrPath = projId;
			reviewService.review(projectIdOrPath, mrIid);
			return ResponseEntity.ok("review started");
		} else if ("close".equalsIgnoreCase(action) || "closed".equalsIgnoreCase(action)
			|| "merge".equalsIgnoreCase(action) || "merged".equalsIgnoreCase(action)) {

			// 닫힌/병합된 MR에 대한 처리
			log.info("MR !{} 이 닫혔습니다. 리뷰 생성하지 않음", mrIid);
			return ResponseEntity.ok("mr closed - no review needed");

		} else {
			// 기타 알 수 없는 action
			log.debug("처리하지 않는 action: {}", action);
			return ResponseEntity.ok("action ignored");
		}
	}
}
