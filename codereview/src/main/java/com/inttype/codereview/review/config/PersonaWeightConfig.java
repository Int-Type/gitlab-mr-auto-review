package com.inttype.codereview.review.config;

import java.util.Map;
import java.util.Set;

import com.inttype.codereview.review.domain.PersonaType;

/**
 * 페르소나별 가중치 설정을 관리하는 클래스
 *
 * <p>파일 패턴, 키워드, 변경 유형에 따라 각 페르소나에게 부여할 점수를 정의합니다.
 * 이 설정을 기반으로 diff 분석 후 가장 적합한 페르소나가 선택됩니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
public class PersonaWeightConfig {

	/** 페르소나 선택을 위한 최소 임계값 (100점 만점 기준) */
	public static final int SELECTION_THRESHOLD = 40;

	/** 추가 점검 멘트를 위한 임계값 (100점 만점 기준) */
	public static final int MENTION_THRESHOLD = 60;

	/**
	 * 파일 경로 패턴별 페르소나 가중치
	 * key: 파일 경로에 포함될 키워드
	 * value: PersonaType별 점수 (최대 40점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> FILE_PATH_WEIGHTS = Map.of(
		// 컨트롤러 레이어 - 보안과 검증이 중요
		"controller", Map.of(
			PersonaType.SECURITY_AUDITOR, 35,
			PersonaType.BUSINESS_ANALYST, 25,
			PersonaType.ARCHITECT, 15
		),

		// 서비스 레이어 - 비즈니스 로직이 핵심
		"service", Map.of(
			PersonaType.BUSINESS_ANALYST, 40,
			PersonaType.ARCHITECT, 25,
			PersonaType.PERFORMANCE_TUNER, 20
		),

		// 리포지토리 레이어 - 데이터 접근과 성능
		"repository", Map.of(
			PersonaType.DATA_GUARDIAN, 40,
			PersonaType.PERFORMANCE_TUNER, 30,
			PersonaType.ARCHITECT, 15
		),

		// 엔티티/도메인 - 아키텍처와 데이터 모델링
		"entity", Map.of(
			PersonaType.DATA_GUARDIAN, 35,
			PersonaType.ARCHITECT, 30,
			PersonaType.BUSINESS_ANALYST, 20
		),

		// 설정 파일 - 보안과 아키텍처
		"config", Map.of(
			PersonaType.SECURITY_AUDITOR, 35,
			PersonaType.ARCHITECT, 30,
			PersonaType.PERFORMANCE_TUNER, 20
		),

		// 테스트 파일 - 품질 관리
		"test", Map.of(
			PersonaType.QUALITY_COACH, 40,
			PersonaType.BUSINESS_ANALYST, 25
		)
	);

	/**
	 * 코드 키워드별 페르소나 가중치
	 * key: diff 내용에 포함될 키워드
	 * value: PersonaType별 점수 (최대 30점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> KEYWORD_WEIGHTS = Map.of(
		// 보안 관련 키워드
		"@PreAuthorize", Map.of(PersonaType.SECURITY_AUDITOR, 30),
		"@Secured", Map.of(PersonaType.SECURITY_AUDITOR, 30),
		"password", Map.of(PersonaType.SECURITY_AUDITOR, 25),
		"token", Map.of(PersonaType.SECURITY_AUDITOR, 25),
		"authentication", Map.of(PersonaType.SECURITY_AUDITOR, 25),
		"authorization", Map.of(PersonaType.SECURITY_AUDITOR, 25),

		// 데이터베이스 관련 키워드
		"@Query", Map.of(PersonaType.DATA_GUARDIAN, 30, PersonaType.PERFORMANCE_TUNER, 20),
		"@Transactional", Map.of(PersonaType.DATA_GUARDIAN, 25, PersonaType.ARCHITECT, 15),
		"SELECT", Map.of(PersonaType.DATA_GUARDIAN, 25, PersonaType.PERFORMANCE_TUNER, 20),
		"INSERT", Map.of(PersonaType.DATA_GUARDIAN, 25),
		"UPDATE", Map.of(PersonaType.DATA_GUARDIAN, 25),
		"DELETE", Map.of(PersonaType.DATA_GUARDIAN, 25),
		"JOIN", Map.of(PersonaType.PERFORMANCE_TUNER, 25, PersonaType.DATA_GUARDIAN, 20),

		// 성능 관련 키워드
		"@Cacheable", Map.of(PersonaType.PERFORMANCE_TUNER, 30),
		"@Async", Map.of(PersonaType.PERFORMANCE_TUNER, 25, PersonaType.ARCHITECT, 15),
		"CompletableFuture", Map.of(PersonaType.PERFORMANCE_TUNER, 25),
		"Parallel", Map.of(PersonaType.PERFORMANCE_TUNER, 25),
		"Stream", Map.of(PersonaType.PERFORMANCE_TUNER, 20),

		// 아키텍처 관련 키워드
		"@Component", Map.of(PersonaType.ARCHITECT, 20),
		"@Service", Map.of(PersonaType.ARCHITECT, 20),
		"@Repository", Map.of(PersonaType.ARCHITECT, 20),
		"interface", Map.of(PersonaType.ARCHITECT, 25),
		"abstract", Map.of(PersonaType.ARCHITECT, 25),

		// 비즈니스 로직 관련 키워드
		"validate", Map.of(PersonaType.BUSINESS_ANALYST, 25),
		"calculate", Map.of(PersonaType.BUSINESS_ANALYST, 25),
		"process", Map.of(PersonaType.BUSINESS_ANALYST, 20),
		"business", Map.of(PersonaType.BUSINESS_ANALYST, 20),

		// 테스트 관련 키워드
		"@Test", Map.of(PersonaType.QUALITY_COACH, 30),
		"@Mock", Map.of(PersonaType.QUALITY_COACH, 25),
		"assert", Map.of(PersonaType.QUALITY_COACH, 25),
		"verify", Map.of(PersonaType.QUALITY_COACH, 25)
	);

	/**
	 * 파일 확장자별 페르소나 가중치
	 * key: 파일 확장자
	 * value: PersonaType별 점수 (최대 20점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> FILE_EXTENSION_WEIGHTS = Map.of(
		".java", Map.of(
			PersonaType.QUALITY_COACH, 10,
			PersonaType.ARCHITECT, 10
		),
		".sql", Map.of(
			PersonaType.DATA_GUARDIAN, 20,
			PersonaType.PERFORMANCE_TUNER, 15
		),
		".properties", Map.of(
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.ARCHITECT, 15
		),
		".yml", Map.of(
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.ARCHITECT, 15
		),
		".yaml", Map.of(
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.ARCHITECT, 15
		)
	);

	/**
	 * 복잡도 패턴별 페르소나 가중치
	 * key: 복잡도를 나타내는 패턴
	 * value: PersonaType별 점수 (최대 15점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> COMPLEXITY_WEIGHTS = Map.of(
		// 복잡한 조건문
		"if.*else.*if", Map.of(PersonaType.BUSINESS_ANALYST, 15, PersonaType.QUALITY_COACH, 10),
		"switch.*case", Map.of(PersonaType.BUSINESS_ANALYST, 15, PersonaType.QUALITY_COACH, 10),

		// 반복문
		"for.*for", Map.of(PersonaType.PERFORMANCE_TUNER, 15, PersonaType.QUALITY_COACH, 10),
		"while.*while", Map.of(PersonaType.PERFORMANCE_TUNER, 15, PersonaType.QUALITY_COACH, 10),

		// 예외 처리
		"try.*catch", Map.of(PersonaType.ARCHITECT, 15, PersonaType.QUALITY_COACH, 10),
		"throw.*Exception", Map.of(PersonaType.ARCHITECT, 15)
	);

	/**
	 * 특정 키워드가 보안 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> SECURITY_KEYWORDS = Set.of(
		"password", "token", "secret", "key", "auth", "login", "session",
		"encrypt", "decrypt", "hash", "salt", "jwt", "oauth", "security"
	);

	/**
	 * 특정 키워드가 성능 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> PERFORMANCE_KEYWORDS = Set.of(
		"cache", "async", "parallel", "concurrent", "thread", "pool",
		"optimization", "performance", "memory", "cpu", "latency"
	);

	/**
	 * 특정 키워드가 데이터베이스 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> DATABASE_KEYWORDS = Set.of(
		"sql", "query", "database", "table", "index", "transaction",
		"commit", "rollback", "lock", "constraint", "foreign", "primary"
	);
}
