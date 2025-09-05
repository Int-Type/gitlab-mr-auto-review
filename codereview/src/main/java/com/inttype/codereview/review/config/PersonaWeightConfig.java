package com.inttype.codereview.review.config;

import java.util.HashMap;
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
	public static final Map<String, Map<PersonaType, Integer>> FILE_PATH_WEIGHTS = createFilePathWeights();

	/**
	 * 코드 키워드별 페르소나 가중치
	 * key: diff 내용에 포함될 키워드
	 * value: PersonaType별 점수 (최대 30점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> KEYWORD_WEIGHTS = createKeywordWeights();

	/**
	 * 파일 확장자별 페르소나 가중치
	 * key: 파일 확장자
	 * value: PersonaType별 점수 (최대 20점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> FILE_EXTENSION_WEIGHTS = createFileExtensionWeights();

	/**
	 * 복잡도 패턴별 페르소나 가중치
	 * key: 복잡도를 나타내는 패턴
	 * value: PersonaType별 점수 (최대 15점)
	 */
	public static final Map<String, Map<PersonaType, Integer>> COMPLEXITY_WEIGHTS = createComplexityWeights();

	/**
	 * 특정 키워드가 보안 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> SECURITY_KEYWORDS = Set.of(
		"password", "token", "secret", "key", "auth", "login", "session",
		"encrypt", "decrypt", "hash", "salt", "jwt", "oauth", "security",
		"csrf", "xss", "cors", "ssl", "tls", "certificate", "firewall"
	);

	/**
	 * 특정 키워드가 성능 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> PERFORMANCE_KEYWORDS = Set.of(
		"cache", "async", "parallel", "concurrent", "thread", "pool",
		"optimization", "performance", "memory", "cpu", "latency",
		"throughput", "scalability", "bottleneck", "profiling", "benchmark"
	);

	/**
	 * 특정 키워드가 데이터베이스 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> DATABASE_KEYWORDS = Set.of(
		"sql", "query", "database", "table", "index", "transaction",
		"commit", "rollback", "lock", "constraint", "foreign", "primary",
		"postgresql", "redis", "mongodb", "elasticsearch", "weaviate"
	);

	/**
	 * 특정 키워드가 프론트엔드 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> FRONTEND_KEYWORDS = Set.of(
		"react", "vue", "angular", "javascript", "typescript", "html", "css",
		"component", "props", "state", "hook", "dom", "event", "render",
		"jsx", "tsx", "scss", "sass", "webpack", "vite", "babel"
	);

	/**
	 * 특정 키워드가 백엔드 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> BACKEND_KEYWORDS = Set.of(
		"spring", "boot", "java", "python", "fastapi", "api", "rest",
		"controller", "service", "repository", "entity", "dto", "model",
		"endpoint", "request", "response", "middleware", "filter"
	);

	/**
	 * 특정 키워드가 데브옵스 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> DEVOPS_KEYWORDS = Set.of(
		"docker", "kubernetes", "jenkins", "nginx", "prometheus", "grafana",
		"deployment", "pipeline", "ci", "cd", "monitoring", "logging",
		"infrastructure", "terraform", "ansible", "helm", "istio"
	);

	/**
	 * 특정 키워드가 데이터 사이언스 관련인지 확인하는 키워드 집합
	 */
	public static final Set<String> DATA_SCIENCE_KEYWORDS = Set.of(
		"pandas", "numpy", "sklearn", "tensorflow", "pytorch", "keras",
		"model", "training", "prediction", "feature", "dataset", "ml",
		"ai", "recommendation", "embedding", "vector", "similarity",
		"clustering", "classification", "regression", "deep", "learning"
	);

	/**
	 * 파일 경로 가중치를 생성합니다.
	 *
	 * @return 파일 경로별 페르소나 가중치 맵
	 */
	private static Map<String, Map<PersonaType, Integer>> createFilePathWeights() {
		Map<String, Map<PersonaType, Integer>> weights = new HashMap<>();

		// ============ 백엔드 관련 ============
		// 컨트롤러 레이어 - 보안과 백엔드 전문성
		weights.put("controller", Map.of(
			PersonaType.BACKEND_SPECIALIST, 40,
			PersonaType.SECURITY_AUDITOR, 35,
			PersonaType.BUSINESS_ANALYST, 25
		));

		// 서비스 레이어 - 백엔드와 비즈니스 로직
		weights.put("service", Map.of(
			PersonaType.BACKEND_SPECIALIST, 40,
			PersonaType.BUSINESS_ANALYST, 35,
			PersonaType.ARCHITECT, 20
		));

		// 리포지토리 레이어 - 데이터와 백엔드
		weights.put("repository", Map.of(
			PersonaType.DATA_GUARDIAN, 40,
			PersonaType.BACKEND_SPECIALIST, 30,
			PersonaType.PERFORMANCE_TUNER, 25
		));

		// 엔티티/도메인 - 데이터와 아키텍처
		weights.put("entity", Map.of(
			PersonaType.DATA_GUARDIAN, 35,
			PersonaType.BACKEND_SPECIALIST, 30,
			PersonaType.ARCHITECT, 25
		));

		// 설정 파일 - 보안과 데브옵스
		weights.put("config", Map.of(
			PersonaType.DEVOPS_ENGINEER, 35,
			PersonaType.SECURITY_AUDITOR, 30,
			PersonaType.BACKEND_SPECIALIST, 25
		));

		// API 관련 - 백엔드와 아키텍처
		weights.put("api", Map.of(
			PersonaType.BACKEND_SPECIALIST, 40,
			PersonaType.ARCHITECT, 30,
			PersonaType.SECURITY_AUDITOR, 25
		));

		// ============ 프론트엔드 관련 ============
		// React 컴포넌트
		weights.put("component", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 40,
			PersonaType.QUALITY_COACH, 25,
			PersonaType.ARCHITECT, 20
		));

		// 페이지/뷰
		weights.put("page", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 40,
			PersonaType.BUSINESS_ANALYST, 25
		));

		// 훅스
		weights.put("hook", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 40,
			PersonaType.PERFORMANCE_TUNER, 25
		));

		// 스타일
		weights.put("style", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 40,
			PersonaType.QUALITY_COACH, 20
		));

		// ============ 데이터 사이언스 관련 ============
		// 머신러닝 모델
		weights.put("model", Map.of(
			PersonaType.DATA_SCIENTIST, 40,
			PersonaType.PERFORMANCE_TUNER, 25,
			PersonaType.ARCHITECT, 20
		));

		// 데이터 처리
		weights.put("data", Map.of(
			PersonaType.DATA_SCIENTIST, 35,
			PersonaType.DATA_GUARDIAN, 30,
			PersonaType.PERFORMANCE_TUNER, 25
		));

		// 분석/추천
		weights.put("analysis", Map.of(
			PersonaType.DATA_SCIENTIST, 40,
			PersonaType.BUSINESS_ANALYST, 25
		));

		weights.put("recommendation", Map.of(
			PersonaType.DATA_SCIENTIST, 40,
			PersonaType.BUSINESS_ANALYST, 30
		));

		// ============ 인프라/데브옵스 관련 ============
		// Docker
		weights.put("docker", Map.of(
			PersonaType.DEVOPS_ENGINEER, 40,
			PersonaType.SECURITY_AUDITOR, 25,
			PersonaType.PERFORMANCE_TUNER, 20
		));

		// Kubernetes
		weights.put("k8s", Map.of(
			PersonaType.DEVOPS_ENGINEER, 40,
			PersonaType.ARCHITECT, 25,
			PersonaType.SECURITY_AUDITOR, 20
		));

		// CI/CD
		weights.put("pipeline", Map.of(
			PersonaType.DEVOPS_ENGINEER, 40,
			PersonaType.QUALITY_COACH, 25
		));

		// 모니터링
		weights.put("monitoring", Map.of(
			PersonaType.DEVOPS_ENGINEER, 40,
			PersonaType.PERFORMANCE_TUNER, 30
		));

		// ============ 테스트 관련 ============
		weights.put("test", Map.of(
			PersonaType.QUALITY_COACH, 40,
			PersonaType.BACKEND_SPECIALIST, 20,
			PersonaType.FRONTEND_SPECIALIST, 20
		));

		return weights;
	}

	/**
	 * 키워드 가중치를 생성합니다.
	 *
	 * @return 키워드별 페르소나 가중치 맵
	 */
	private static Map<String, Map<PersonaType, Integer>> createKeywordWeights() {
		Map<String, Map<PersonaType, Integer>> weights = new HashMap<>();

		// ============ 보안 관련 키워드들 ============
		weights.put("@PreAuthorize", Map.of(PersonaType.SECURITY_AUDITOR, 30));
		weights.put("@Secured", Map.of(PersonaType.SECURITY_AUDITOR, 30));
		weights.put("password", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("token", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("authentication", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("authorization", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("jwt", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("oauth", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("encrypt", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("decrypt", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("hash", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("csrf", Map.of(PersonaType.SECURITY_AUDITOR, 25));
		weights.put("xss", Map.of(PersonaType.SECURITY_AUDITOR, 25));

		// ============ 데이터베이스 관련 키워드들 ============
		weights.put("@Query", Map.of(PersonaType.DATA_GUARDIAN, 30, PersonaType.PERFORMANCE_TUNER, 20));
		weights.put("@Transactional", Map.of(PersonaType.DATA_GUARDIAN, 25, PersonaType.ARCHITECT, 15));
		weights.put("SELECT", Map.of(PersonaType.DATA_GUARDIAN, 25, PersonaType.PERFORMANCE_TUNER, 20));
		weights.put("INSERT", Map.of(PersonaType.DATA_GUARDIAN, 25));
		weights.put("UPDATE", Map.of(PersonaType.DATA_GUARDIAN, 25));
		weights.put("DELETE", Map.of(PersonaType.DATA_GUARDIAN, 25));
		weights.put("JOIN", Map.of(PersonaType.PERFORMANCE_TUNER, 25, PersonaType.DATA_GUARDIAN, 20));
		weights.put("INDEX", Map.of(PersonaType.DATA_GUARDIAN, 25, PersonaType.PERFORMANCE_TUNER, 20));
		weights.put("postgresql", Map.of(PersonaType.DATA_GUARDIAN, 25));
		weights.put("redis", Map.of(PersonaType.DATA_GUARDIAN, 25, PersonaType.PERFORMANCE_TUNER, 20));
		weights.put("weaviate", Map.of(PersonaType.DATA_SCIENTIST, 30, PersonaType.DATA_GUARDIAN, 20));

		// ============ 성능 관련 키워드들 ============
		weights.put("@Cacheable", Map.of(PersonaType.PERFORMANCE_TUNER, 30));
		weights.put("@Async", Map.of(PersonaType.PERFORMANCE_TUNER, 25, PersonaType.ARCHITECT, 15));
		weights.put("CompletableFuture", Map.of(PersonaType.PERFORMANCE_TUNER, 25));
		weights.put("Parallel", Map.of(PersonaType.PERFORMANCE_TUNER, 25));
		weights.put("Stream", Map.of(PersonaType.PERFORMANCE_TUNER, 20));
		weights.put("cache", Map.of(PersonaType.PERFORMANCE_TUNER, 25));
		weights.put("optimization", Map.of(PersonaType.PERFORMANCE_TUNER, 25));
		weights.put("latency", Map.of(PersonaType.PERFORMANCE_TUNER, 25));
		weights.put("throughput", Map.of(PersonaType.PERFORMANCE_TUNER, 25));

		// ============ 백엔드 관련 키워드들 ============
		// Spring Boot
		weights.put("@RestController", Map.of(PersonaType.BACKEND_SPECIALIST, 30));
		weights.put("@Service", Map.of(PersonaType.BACKEND_SPECIALIST, 25, PersonaType.ARCHITECT, 15));
		weights.put("@Repository", Map.of(PersonaType.BACKEND_SPECIALIST, 25, PersonaType.DATA_GUARDIAN, 15));
		weights.put("@Component", Map.of(PersonaType.BACKEND_SPECIALIST, 20, PersonaType.ARCHITECT, 15));
		weights.put("@Autowired", Map.of(PersonaType.BACKEND_SPECIALIST, 20));
		weights.put("@RequestMapping", Map.of(PersonaType.BACKEND_SPECIALIST, 25));
		weights.put("@GetMapping", Map.of(PersonaType.BACKEND_SPECIALIST, 25));
		weights.put("@PostMapping", Map.of(PersonaType.BACKEND_SPECIALIST, 25));
		weights.put("SpringApplication", Map.of(PersonaType.BACKEND_SPECIALIST, 25));

		// FastAPI
		weights.put("FastAPI", Map.of(PersonaType.BACKEND_SPECIALIST, 30, PersonaType.DATA_SCIENTIST, 20));
		weights.put("pydantic", Map.of(PersonaType.BACKEND_SPECIALIST, 25, PersonaType.DATA_SCIENTIST, 20));
		weights.put("uvicorn", Map.of(PersonaType.BACKEND_SPECIALIST, 20));

		// ============ 프론트엔드 관련 키워드들 ============
		// React
		weights.put("useState", Map.of(PersonaType.FRONTEND_SPECIALIST, 30));
		weights.put("useEffect", Map.of(PersonaType.FRONTEND_SPECIALIST, 30));
		weights.put("useContext", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("useReducer", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("component", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("props", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("state", Map.of(PersonaType.FRONTEND_SPECIALIST, 20));
		weights.put("jsx", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("tsx", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));

		// JavaScript/TypeScript
		weights.put("typescript", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("interface", Map.of(PersonaType.FRONTEND_SPECIALIST, 20, PersonaType.ARCHITECT, 15));
		weights.put("type", Map.of(PersonaType.FRONTEND_SPECIALIST, 20));

		// CSS/Styling
		weights.put("styled-components", Map.of(PersonaType.FRONTEND_SPECIALIST, 25));
		weights.put("css", Map.of(PersonaType.FRONTEND_SPECIALIST, 20));
		weights.put("scss", Map.of(PersonaType.FRONTEND_SPECIALIST, 20));
		weights.put("tailwind", Map.of(PersonaType.FRONTEND_SPECIALIST, 20));

		// ============ 데이터 사이언스 관련 키워드들 ============
		// 머신러닝
		weights.put("sklearn", Map.of(PersonaType.DATA_SCIENTIST, 30));
		weights.put("tensorflow", Map.of(PersonaType.DATA_SCIENTIST, 30));
		weights.put("pytorch", Map.of(PersonaType.DATA_SCIENTIST, 30));
		weights.put("keras", Map.of(PersonaType.DATA_SCIENTIST, 30));
		weights.put("model", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("predict", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("train", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("fit", Map.of(PersonaType.DATA_SCIENTIST, 25));

		// 데이터 처리
		weights.put("pandas", Map.of(PersonaType.DATA_SCIENTIST, 30));
		weights.put("numpy", Map.of(PersonaType.DATA_SCIENTIST, 30));
		weights.put("dataframe", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("array", Map.of(PersonaType.DATA_SCIENTIST, 20));
		weights.put("preprocessing", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("feature", Map.of(PersonaType.DATA_SCIENTIST, 25));

		// 추천시스템
		weights.put("recommendation", Map.of(PersonaType.DATA_SCIENTIST, 30, PersonaType.BUSINESS_ANALYST, 20));
		weights.put("collaborative", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("content-based", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("embedding", Map.of(PersonaType.DATA_SCIENTIST, 25));
		weights.put("similarity", Map.of(PersonaType.DATA_SCIENTIST, 25));

		// ============ 데브옵스 관련 키워드들 ============
		// Docker
		weights.put("FROM", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("RUN", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("COPY", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("ENV", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("EXPOSE", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("docker", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("dockerfile", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("docker-compose", Map.of(PersonaType.DEVOPS_ENGINEER, 30));

		// Kubernetes
		weights.put("kubernetes", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("kubectl", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("deployment", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("ingress", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("namespace", Map.of(PersonaType.DEVOPS_ENGINEER, 25));

		// CI/CD
		weights.put("jenkins", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("pipeline", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("build", Map.of(PersonaType.DEVOPS_ENGINEER, 20));
		weights.put("deploy", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("github-actions", Map.of(PersonaType.DEVOPS_ENGINEER, 25));

		// 모니터링
		weights.put("prometheus", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("grafana", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("loki", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("promtail", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("metrics", Map.of(PersonaType.DEVOPS_ENGINEER, 25, PersonaType.PERFORMANCE_TUNER, 20));
		weights.put("logging", Map.of(PersonaType.DEVOPS_ENGINEER, 25));

		// Nginx
		weights.put("nginx", Map.of(PersonaType.DEVOPS_ENGINEER, 30));
		weights.put("proxy_pass", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("upstream", Map.of(PersonaType.DEVOPS_ENGINEER, 25));
		weights.put("location", Map.of(PersonaType.DEVOPS_ENGINEER, 20));

		// ============ 아키텍처 관련 키워드들 ============
		weights.put("abstract", Map.of(PersonaType.ARCHITECT, 25));
		weights.put("pattern", Map.of(PersonaType.ARCHITECT, 25));
		weights.put("design", Map.of(PersonaType.ARCHITECT, 20));
		weights.put("architecture", Map.of(PersonaType.ARCHITECT, 30));
		weights.put("dependency", Map.of(PersonaType.ARCHITECT, 25));
		weights.put("injection", Map.of(PersonaType.ARCHITECT, 25));

		// ============ 비즈니스 로직 관련 키워드들 ============
		weights.put("validate", Map.of(PersonaType.BUSINESS_ANALYST, 25));
		weights.put("calculate", Map.of(PersonaType.BUSINESS_ANALYST, 25));
		weights.put("process", Map.of(PersonaType.BUSINESS_ANALYST, 20));
		weights.put("business", Map.of(PersonaType.BUSINESS_ANALYST, 20));
		weights.put("workflow", Map.of(PersonaType.BUSINESS_ANALYST, 25));
		weights.put("rule", Map.of(PersonaType.BUSINESS_ANALYST, 25));

		// ============ 테스트 관련 키워드들 ============
		weights.put("@Test", Map.of(PersonaType.QUALITY_COACH, 30));
		weights.put("@Mock", Map.of(PersonaType.QUALITY_COACH, 25));
		weights.put("assert", Map.of(PersonaType.QUALITY_COACH, 25));
		weights.put("verify", Map.of(PersonaType.QUALITY_COACH, 25));
		weights.put("expect", Map.of(PersonaType.QUALITY_COACH, 25));
		weights.put("jest", Map.of(PersonaType.QUALITY_COACH, 25, PersonaType.FRONTEND_SPECIALIST, 20));
		weights.put("junit", Map.of(PersonaType.QUALITY_COACH, 25, PersonaType.BACKEND_SPECIALIST, 15));
		weights.put("pytest", Map.of(PersonaType.QUALITY_COACH, 25, PersonaType.DATA_SCIENTIST, 15));

		return weights;
	}

	/**
	 * 파일 확장자 가중치를 생성합니다.
	 *
	 * @return 파일 확장자별 페르소나 가중치 맵
	 */
	private static Map<String, Map<PersonaType, Integer>> createFileExtensionWeights() {
		Map<String, Map<PersonaType, Integer>> weights = new HashMap<>();

		// ============ 백엔드 파일들 ============
		weights.put(".java", Map.of(
			PersonaType.BACKEND_SPECIALIST, 25,
			PersonaType.QUALITY_COACH, 15,
			PersonaType.ARCHITECT, 10
		));

		weights.put(".py", Map.of(
			PersonaType.DATA_SCIENTIST, 30,
			PersonaType.BACKEND_SPECIALIST, 20,
			PersonaType.QUALITY_COACH, 15
		));

		weights.put(".sql", Map.of(
			PersonaType.DATA_GUARDIAN, 30,
			PersonaType.PERFORMANCE_TUNER, 20,
			PersonaType.BACKEND_SPECIALIST, 15
		));

		// ============ 프론트엔드 파일들 ============
		weights.put(".js", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 30,
			PersonaType.QUALITY_COACH, 15,
			PersonaType.PERFORMANCE_TUNER, 10
		));

		weights.put(".jsx", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 35,
			PersonaType.QUALITY_COACH, 15
		));

		weights.put(".ts", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 30,
			PersonaType.QUALITY_COACH, 20,
			PersonaType.ARCHITECT, 15
		));

		weights.put(".tsx", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 35,
			PersonaType.QUALITY_COACH, 20
		));

		weights.put(".vue", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 35,
			PersonaType.QUALITY_COACH, 15
		));

		weights.put(".html", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 30,
			PersonaType.QUALITY_COACH, 10
		));

		weights.put(".css", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 25,
			PersonaType.QUALITY_COACH, 10
		));

		weights.put(".scss", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 25,
			PersonaType.QUALITY_COACH, 10
		));

		weights.put(".sass", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 25,
			PersonaType.QUALITY_COACH, 10
		));

		// ============ 인프라/데브옵스 파일들 ============
		weights.put(".dockerfile", Map.of(
			PersonaType.DEVOPS_ENGINEER, 40,
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.PERFORMANCE_TUNER, 15
		));

		weights.put(".yml", Map.of(
			PersonaType.DEVOPS_ENGINEER, 30,
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.ARCHITECT, 15
		));

		weights.put(".yaml", Map.of(
			PersonaType.DEVOPS_ENGINEER, 30,
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.ARCHITECT, 15
		));

		weights.put(".tf", Map.of(
			PersonaType.DEVOPS_ENGINEER, 35,
			PersonaType.SECURITY_AUDITOR, 25,
			PersonaType.ARCHITECT, 15
		));

		weights.put(".sh", Map.of(
			PersonaType.DEVOPS_ENGINEER, 30,
			PersonaType.SECURITY_AUDITOR, 20
		));

		weights.put(".conf", Map.of(
			PersonaType.DEVOPS_ENGINEER, 25,
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.PERFORMANCE_TUNER, 15
		));

		// ============ 설정 파일들 ============
		weights.put(".properties", Map.of(
			PersonaType.BACKEND_SPECIALIST, 20,
			PersonaType.SECURITY_AUDITOR, 20,
			PersonaType.DEVOPS_ENGINEER, 15
		));

		weights.put(".json", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 20,
			PersonaType.BACKEND_SPECIALIST, 15,
			PersonaType.DEVOPS_ENGINEER, 15
		));

		weights.put(".xml", Map.of(
			PersonaType.BACKEND_SPECIALIST, 20,
			PersonaType.DEVOPS_ENGINEER, 15
		));

		// ============ 데이터 사이언스 파일들 ============
		weights.put(".ipynb", Map.of(
			PersonaType.DATA_SCIENTIST, 40,
			PersonaType.QUALITY_COACH, 15
		));

		weights.put(".pkl", Map.of(
			PersonaType.DATA_SCIENTIST, 35,
			PersonaType.PERFORMANCE_TUNER, 15
		));

		weights.put(".csv", Map.of(
			PersonaType.DATA_SCIENTIST, 25,
			PersonaType.DATA_GUARDIAN, 20
		));

		weights.put(".parquet", Map.of(
			PersonaType.DATA_SCIENTIST, 30,
			PersonaType.PERFORMANCE_TUNER, 25
		));

		// ============ 빌드/패키지 관리 파일들 ============
		weights.put(".gradle", Map.of(
			PersonaType.BACKEND_SPECIALIST, 25,
			PersonaType.DEVOPS_ENGINEER, 20,
			PersonaType.ARCHITECT, 15
		));

		weights.put("package.json", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 30,
			PersonaType.DEVOPS_ENGINEER, 20
		));

		weights.put("requirements.txt", Map.of(
			PersonaType.DATA_SCIENTIST, 30,
			PersonaType.BACKEND_SPECIALIST, 20,
			PersonaType.DEVOPS_ENGINEER, 15
		));

		weights.put("poetry.lock", Map.of(
			PersonaType.DATA_SCIENTIST, 25,
			PersonaType.BACKEND_SPECIALIST, 20
		));

		return weights;
	}

	/**
	 * 복잡도 패턴 가중치를 생성합니다.
	 *
	 * @return 복잡도 패턴별 페르소나 가중치 맵
	 */
	private static Map<String, Map<PersonaType, Integer>> createComplexityWeights() {
		Map<String, Map<PersonaType, Integer>> weights = new HashMap<>();

		// 복잡한 조건문 - 비즈니스 로직과 품질에 영향
		weights.put("if.*else.*if", Map.of(
			PersonaType.BUSINESS_ANALYST, 15,
			PersonaType.QUALITY_COACH, 10,
			PersonaType.BACKEND_SPECIALIST, 10
		));
		weights.put("switch.*case", Map.of(
			PersonaType.BUSINESS_ANALYST, 15,
			PersonaType.QUALITY_COACH, 10,
			PersonaType.BACKEND_SPECIALIST, 10
		));

		// 중첩 반복문 - 성능에 직접적 영향
		weights.put("for.*for", Map.of(
			PersonaType.PERFORMANCE_TUNER, 15,
			PersonaType.QUALITY_COACH, 10,
			PersonaType.DATA_SCIENTIST, 10
		));
		weights.put("while.*while", Map.of(
			PersonaType.PERFORMANCE_TUNER, 15,
			PersonaType.QUALITY_COACH, 10
		));

		// 예외 처리 - 아키텍처와 품질
		weights.put("try.*catch", Map.of(
			PersonaType.ARCHITECT, 15,
			PersonaType.QUALITY_COACH, 10,
			PersonaType.BACKEND_SPECIALIST, 10
		));
		weights.put("throw.*Exception", Map.of(
			PersonaType.ARCHITECT, 15,
			PersonaType.BACKEND_SPECIALIST, 10
		));

		// 비동기 처리 복잡도 - 성능과 아키텍처
		weights.put("async.*await", Map.of(
			PersonaType.PERFORMANCE_TUNER, 15,
			PersonaType.FRONTEND_SPECIALIST, 12,
			PersonaType.BACKEND_SPECIALIST, 10
		));

		// 복잡한 쿼리 - 데이터와 성능
		weights.put("JOIN.*JOIN", Map.of(
			PersonaType.DATA_GUARDIAN, 15,
			PersonaType.PERFORMANCE_TUNER, 12
		));

		// 복잡한 React 상태 관리
		weights.put("useState.*useEffect", Map.of(
			PersonaType.FRONTEND_SPECIALIST, 15,
			PersonaType.QUALITY_COACH, 10
		));

		return weights;
	}
}
