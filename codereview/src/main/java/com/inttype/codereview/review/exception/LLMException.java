package com.inttype.codereview.review.exception;

/**
 * LLM 서비스 관련 예외를 처리하는 통합 예외 클래스
 * 다양한 LLM API에서 발생하는 예외들을 통합된 형태로 처리하여
 * 일관된 에러 처리를 제공합니다.
 *
 * @author inttype
 * @since 1.0
 */
public class LLMException extends RuntimeException {
	/**
	 * LLM 예외 유형을 정의하는 열거형
	 */
	public enum ErrorType {
		/** API 키가 유효하지 않거나 누락된 경우 */
		INVALID_API_KEY("유효하지 않은 API 키"),

		/** API 요청 한도를 초과한 경우 */
		RATE_LIMIT_EXCEEDED("API 요청 한도 초과"),

		/** 네트워크 연결 문제 */
		NETWORK_ERROR("네트워크 연결 오류"),

		/** API 서버 내부 오류 */
		SERVER_ERROR("서버 내부 오류"),

		/** 요청이나 응답 형식 오류 */
		INVALID_FORMAT("잘못된 요청/응답 형식"),

		/** 기타 알 수 없는 오류 */
		UNKNOWN("알 수 없는 오류");

		private final String description;

		ErrorType(String description) {
			this.description = description;
		}

		public String getDescription() {
			return description;
		}
	}

	private final ErrorType errorType;
	private final String llmProvider;

	/**
	 * LLM 예외를 생성합니다.
	 *
	 * @param errorType 예외 유형
	 * @param llmProvider LLM 제공자 (예: "OpenAI", "Claude", "Gemini")
	 * @param message 상세 메시지
	 */
	public LLMException(ErrorType errorType, String llmProvider, String message) {
		super(String.format("[%s] %s: %s", llmProvider, errorType.getDescription(), message));
		this.errorType = errorType;
		this.llmProvider = llmProvider;
	}

	/**
	 * LLM 예외를 생성합니다. (원인 예외 포함)
	 *
	 * @param errorType 예외 유형
	 * @param llmProvider LLM 제공자
	 * @param message 상세 메시지
	 * @param cause 원인 예외
	 */
	public LLMException(ErrorType errorType, String llmProvider, String message, Throwable cause) {
		super(String.format("[%s] %s: %s", llmProvider, errorType.getDescription(), message), cause);
		this.errorType = errorType;
		this.llmProvider = llmProvider;
	}

	/**
	 * 예외 유형을 반환합니다.
	 *
	 * @return 예외 유형
	 */
	public ErrorType getErrorType() {
		return errorType;
	}

	/**
	 * LLM 제공자를 반환합니다.
	 *
	 * @return LLM 제공자명
	 */
	public String getLlmProvider() {
		return llmProvider;
	}
}
