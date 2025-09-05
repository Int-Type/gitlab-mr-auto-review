package com.inttype.codereview.review.domain;

/**
 * 코드 리뷰 페르소나 유형을 정의하는 열거형
 *
 * <p>각 페르소나는 특정 전문 영역에 특화된 코드 리뷰를 담당합니다.
 * diff 분석 결과에 따라 가장 적합한 페르소나가 선택되어 리뷰를 수행합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
public enum PersonaType {
	/**
	 * 기본 리뷰어 - 전체적인 코드 품질 검토
	 * 특정 영역에 특화되지 않은 일반적인 리뷰 담당
	 */
	GENERAL_REVIEWER("General Reviewer", "전체적인 코드 품질과 기본적인 개선사항을 검토합니다."),

	/**
	 * 보안 감사관 - 보안 취약점 및 보안 이슈 전문
	 * 인증, 인가, 입력 검증, 데이터 노출 등 보안 관련 검토
	 */
	SECURITY_AUDITOR("Security Auditor", "보안 취약점과 보안 위험 요소를 전문적으로 검토합니다."),

	/**
	 * 성능 튜너 - 성능 최적화 및 확장성 전문
	 * 쿼리 최적화, 메모리 사용, 병목 지점 등 성능 관련 검토
	 */
	PERFORMANCE_TUNER("Performance Tuner", "성능 병목과 최적화 포인트를 전문적으로 검토합니다."),

	/**
	 * 데이터 가디언 - 데이터베이스 및 데이터 무결성 전문
	 * 쿼리, 트랜잭션, 데이터 정합성 등 데이터 관련 검토
	 */
	DATA_GUARDIAN("Data Guardian", "데이터베이스 쿼리와 데이터 무결성을 전문적으로 검토합니다."),

	/**
	 * 비즈니스 분석가 - 비즈니스 로직 및 도메인 규칙 전문
	 * 요구사항 구현, 비즈니스 규칙 준수 등 도메인 로직 검토
	 */
	BUSINESS_ANALYST("Business Analyst", "비즈니스 로직과 도메인 규칙 구현을 전문적으로 검토합니다."),

	/**
	 * 아키텍트 - 아키텍처 및 설계 원칙 전문
	 * 레이어 분리, 의존성, 모듈 구조 등 아키텍처 관련 검토
	 */
	ARCHITECT("Architect", "아키텍처 설계와 모듈 구조를 전문적으로 검토합니다."),

	/**
	 * 품질 코치 - 테스트 및 코드 품질 전문
	 * 테스트 코드, 가독성, 컨벤션 등 코드 품질 검토
	 */
	QUALITY_COACH("Quality Coach", "테스트 전략과 코드 가독성을 전문적으로 검토합니다.");

	private final String displayName;
	private final String description;

	PersonaType(String displayName, String description) {
		this.displayName = displayName;
		this.description = description;
	}

	/**
	 * 페르소나의 표시명을 반환합니다.
	 *
	 * @return 페르소나 표시명
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * 페르소나의 상세 설명을 반환합니다.
	 *
	 * @return 페르소나 설명
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * 페르소나의 전문성을 나타내는 이모지를 반환합니다.
	 *
	 * @return 페르소나별 이모지
	 */
	public String getEmoji() {
		return switch (this) {
			case GENERAL_REVIEWER -> "🤖";
			case SECURITY_AUDITOR -> "🔒";
			case PERFORMANCE_TUNER -> "⚡";
			case DATA_GUARDIAN -> "🗃️";
			case BUSINESS_ANALYST -> "💼";
			case ARCHITECT -> "🏗️";
			case QUALITY_COACH -> "✅";
		};
	}
}
