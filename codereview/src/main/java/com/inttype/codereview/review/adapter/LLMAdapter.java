package com.inttype.codereview.review.adapter;

import java.util.List;

import org.gitlab4j.api.models.Diff;

import reactor.core.publisher.Mono;

/**
 * LLM(Large Language Model) 서비스를 위한 통합 어댑터 인터페이스
 * 다양한 LLM API(OpenAI, Claude, Gemini 등)를 통합된 인터페이스로 제공하여
 * 코드 리뷰 생성 기능을 구현합니다.
 *
 * @author inttype
 * @since 1.0
 */
public interface LLMAdapter {
	/**
	 * GitLab MR의 변경사항(diff)을 분석하여 코드 리뷰를 생성합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @param systemPrompt 시스템 프롬프트 (리뷰 스타일 및 규칙 정의)
	 * @return 생성된 코드 리뷰 내용을 포함한 Mono 객체
	 */
	Mono<String> generateReview(List<Diff> diffs, String systemPrompt);

	/**
	 * 어댑터가 지원하는 LLM 모델명을 반환합니다.
	 *
	 * @return 지원하는 모델명
	 */
	String getSupportedModel();

	/**
	 * 어댑터가 현재 사용 가능한 상태인지 확인합니다.
	 * (API 키 존재 여부, 네트워크 연결 상태 등)
	 *
	 * @return 사용 가능한 경우 true, 그렇지 않으면 false
	 */
	boolean isAvailable();
}
