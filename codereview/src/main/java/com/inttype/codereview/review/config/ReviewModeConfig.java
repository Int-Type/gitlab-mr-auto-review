package com.inttype.codereview.review.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import lombok.Data;

/**
 * 리뷰 모드 설정을 관리하는 Configuration Properties
 *
 * <p>페르소나 기반 리뷰와 통합 LLM 리뷰 모드를 선택할 수 있습니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "app.review")
public class ReviewModeConfig {

	/**
	 * 리뷰 모드 ("persona" 또는 "integrated")
	 * - persona: 페르소나 기반 전문화된 리뷰
	 * - integrated: 기존 통합 LLM 리뷰
	 */
	private String mode = "persona";

	/**
	 * 페르소나 모드 사용 여부를 확인합니다.
	 *
	 * @return 페르소나 모드인 경우 true
	 */
	public boolean isPersonaMode() {
		return "persona".equalsIgnoreCase(mode);
	}

	/**
	 * 통합 모드 사용 여부를 확인합니다.
	 *
	 * @return 통합 모드인 경우 true
	 */
	public boolean isIntegratedMode() {
		return "integrated".equalsIgnoreCase(mode);
	}
}
