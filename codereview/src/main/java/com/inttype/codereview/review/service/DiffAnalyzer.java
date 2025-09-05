package com.inttype.codereview.review.service;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gitlab4j.api.models.Diff;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.inttype.codereview.review.config.PersonaWeightConfig;
import com.inttype.codereview.review.domain.PersonaType;

import lombok.extern.slf4j.Slf4j;

/**
 * GitLab MR의 diff를 분석하여 적절한 페르소나를 선택하는 분석기
 *
 * <p>파일 경로, 확장자, 코드 키워드, 복잡도 등을 종합적으로 분석하여
 * 각 페르소나별 점수를 계산하고 최적의 리뷰어를 선택합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Slf4j
@Component
public class DiffAnalyzer {

	/**
	 * diff 목록을 분석하여 각 페르소나별 점수를 계산합니다.
	 *
	 * @param diffs GitLab MR의 변경사항 목록
	 * @return 페르소나별 점수 맵 (100점 만점)
	 */
	public Map<PersonaType, Integer> analyzePersonaScores(List<Diff> diffs) {
		Map<PersonaType, Integer> scores = new EnumMap<>(PersonaType.class);

		// 모든 페르소나 점수를 0으로 초기화
		for (PersonaType persona : PersonaType.values()) {
			scores.put(persona, 0);
		}

		if (diffs == null || diffs.isEmpty()) {
			log.debug("분석할 diff가 없습니다. 기본 페르소나 사용");
			return scores;
		}

		log.debug("diff 분석 시작 - 파일 수: {}", diffs.size());

		for (Diff diff : diffs) {
			analyzeSingleDiff(diff, scores);
		}

		// 점수 정규화 (최대 100점으로 제한)
		normalizeScores(scores);

		log.debug("페르소나별 최종 점수: {}", scores);
		return scores;
	}

	/**
	 * 단일 diff를 분석하여 점수에 반영합니다.
	 *
	 * @param diff 분석할 단일 diff
	 * @param scores 점수를 누적할 맵
	 */
	private void analyzeSingleDiff(Diff diff, Map<PersonaType, Integer> scores) {
		String filePath = getFilePath(diff);
		String diffContent = diff.getDiff();

		if (!StringUtils.hasText(filePath)) {
			return;
		}

		log.debug("파일 분석 중: {}", filePath);

		// 1. 파일 경로 분석
		analyzeFilePath(filePath, scores);

		// 2. 파일 확장자 분석
		analyzeFileExtension(filePath, scores);

		// 3. diff 내용 키워드 분석
		if (StringUtils.hasText(diffContent)) {
			analyzeKeywords(diffContent, scores);
			analyzeComplexity(diffContent, scores);
		}
	}

	/**
	 * 파일 경로를 분석하여 해당하는 페르소나 점수를 증가시킵니다.
	 *
	 * @param filePath 분석할 파일 경로
	 * @param scores 점수를 누적할 맵
	 */
	private void analyzeFilePath(String filePath, Map<PersonaType, Integer> scores) {
		String lowerPath = filePath.toLowerCase();

		PersonaWeightConfig.FILE_PATH_WEIGHTS.forEach((pathKeyword, weights) -> {
			if (lowerPath.contains(pathKeyword)) {
				weights.forEach((persona, weight) -> {
					scores.merge(persona, weight, Integer::sum);
					log.debug("파일 경로 매칭 - {}: {} +{}", pathKeyword, persona, weight);
				});
			}
		});
	}

	/**
	 * 파일 확장자를 분석하여 해당하는 페르소나 점수를 증가시킵니다.
	 *
	 * @param filePath 분석할 파일 경로
	 * @param scores 점수를 누적할 맵
	 */
	private void analyzeFileExtension(String filePath, Map<PersonaType, Integer> scores) {
		PersonaWeightConfig.FILE_EXTENSION_WEIGHTS.forEach((extension, weights) -> {
			if (filePath.endsWith(extension)) {
				weights.forEach((persona, weight) -> {
					scores.merge(persona, weight, Integer::sum);
					log.debug("파일 확장자 매칭 - {}: {} +{}", extension, persona, weight);
				});
			}
		});
	}

	/**
	 * diff 내용의 키워드를 분석하여 해당하는 페르소나 점수를 증가시킵니다.
	 *
	 * @param diffContent 분석할 diff 내용
	 * @param scores 점수를 누적할 맵
	 */
	private void analyzeKeywords(String diffContent, Map<PersonaType, Integer> scores) {
		String lowerContent = diffContent.toLowerCase();

		PersonaWeightConfig.KEYWORD_WEIGHTS.forEach((keyword, weights) -> {
			if (lowerContent.contains(keyword.toLowerCase())) {
				weights.forEach((persona, weight) -> {
					scores.merge(persona, weight, Integer::sum);
					log.debug("키워드 매칭 - {}: {} +{}", keyword, persona, weight);
				});
			}
		});

		// 추가적인 키워드 집합 기반 분석
		analyzeKeywordSets(lowerContent, scores);
	}

	/**
	 * 키워드 집합을 기반으로 추가 점수를 부여합니다.
	 *
	 * @param lowerContent 소문자로 변환된 diff 내용
	 * @param scores 점수를 누적할 맵
	 */
	private void analyzeKeywordSets(String lowerContent, Map<PersonaType, Integer> scores) {
		// 보안 키워드 검사
		long securityMatches = PersonaWeightConfig.SECURITY_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (securityMatches > 0) {
			scores.merge(PersonaType.SECURITY_AUDITOR, (int)Math.min(securityMatches * 5, 20), Integer::sum);
			log.debug("보안 키워드 집합 매칭: {} 개, +{}", securityMatches, Math.min(securityMatches * 5, 20));
		}

		// 성능 키워드 검사
		long performanceMatches = PersonaWeightConfig.PERFORMANCE_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (performanceMatches > 0) {
			scores.merge(PersonaType.PERFORMANCE_TUNER, (int)Math.min(performanceMatches * 5, 20), Integer::sum);
			log.debug("성능 키워드 집합 매칭: {} 개, +{}", performanceMatches, Math.min(performanceMatches * 5, 20));
		}

		// 데이터베이스 키워드 검사
		long databaseMatches = PersonaWeightConfig.DATABASE_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (databaseMatches > 0) {
			scores.merge(PersonaType.DATA_GUARDIAN, (int)Math.min(databaseMatches * 5, 20), Integer::sum);
			log.debug("데이터베이스 키워드 집합 매칭: {} 개, +{}", databaseMatches, Math.min(databaseMatches * 5, 20));
		}

		// 프론트엔드 키워드 검사
		long frontendMatches = PersonaWeightConfig.FRONTEND_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (frontendMatches > 0) {
			scores.merge(PersonaType.FRONTEND_SPECIALIST, (int)Math.min(frontendMatches * 5, 20), Integer::sum);
			log.debug("프론트엔드 키워드 집합 매칭: {} 개, +{}", frontendMatches, Math.min(frontendMatches * 5, 20));
		}

		// 백엔드 키워드 검사
		long backendMatches = PersonaWeightConfig.BACKEND_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (backendMatches > 0) {
			scores.merge(PersonaType.BACKEND_SPECIALIST, (int)Math.min(backendMatches * 5, 20), Integer::sum);
			log.debug("백엔드 키워드 집합 매칭: {} 개, +{}", backendMatches, Math.min(backendMatches * 5, 20));
		}

		// 데브옵스 키워드 검사
		long devopsMatches = PersonaWeightConfig.DEVOPS_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (devopsMatches > 0) {
			scores.merge(PersonaType.DEVOPS_ENGINEER, (int)Math.min(devopsMatches * 5, 20), Integer::sum);
			log.debug("데브옵스 키워드 집합 매칭: {} 개, +{}", devopsMatches, Math.min(devopsMatches * 5, 20));
		}

		// 데이터 사이언스 키워드 검사
		long dataScienceMatches = PersonaWeightConfig.DATA_SCIENCE_KEYWORDS.stream()
			.mapToLong(keyword -> countMatches(lowerContent, keyword))
			.sum();
		if (dataScienceMatches > 0) {
			scores.merge(PersonaType.DATA_SCIENTIST, (int)Math.min(dataScienceMatches * 5, 20), Integer::sum);
			log.debug("데이터 사이언스 키워드 집합 매칭: {} 개, +{}", dataScienceMatches, Math.min(dataScienceMatches * 5, 20));
		}
	}

	/**
	 * diff 내용의 복잡도를 분석하여 해당하는 페르소나 점수를 증가시킵니다.
	 *
	 * @param diffContent 분석할 diff 내용
	 * @param scores 점수를 누적할 맵
	 */
	private void analyzeComplexity(String diffContent, Map<PersonaType, Integer> scores) {
		PersonaWeightConfig.COMPLEXITY_WEIGHTS.forEach((pattern, weights) -> {
			if (Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(diffContent).find()) {
				weights.forEach((persona, weight) -> {
					scores.merge(persona, weight, Integer::sum);
					log.debug("복잡도 패턴 매칭 - {}: {} +{}", pattern, persona, weight);
				});
			}
		});
	}

	/**
	 * 문자열에서 특정 키워드의 출현 횟수를 계산합니다.
	 *
	 * @param content 검색할 문자열
	 * @param keyword 찾을 키워드
	 * @return 출현 횟수
	 */
	private long countMatches(String content, String keyword) {
		return Arrays.stream(content.split("\\s+"))
			.mapToLong(word -> word.contains(keyword) ? 1 : 0)
			.sum();
	}

	/**
	 * 점수를 정규화하여 최대 100점으로 제한합니다.
	 *
	 * @param scores 정규화할 점수 맵
	 */
	private void normalizeScores(Map<PersonaType, Integer> scores) {
		scores.replaceAll((persona, score) -> Math.min(score, 100));
	}

	/**
	 * diff에서 파일 경로를 추출합니다.
	 * 새 파일 경로를 우선하고, 없으면 기존 파일 경로를 사용합니다.
	 *
	 * @param diff 파일 경로를 추출할 diff
	 * @return 파일 경로
	 */
	private String getFilePath(Diff diff) {
		if (StringUtils.hasText(diff.getNewPath())) {
			return diff.getNewPath();
		}
		return diff.getOldPath();
	}

	/**
	 * 분석 결과를 사람이 읽기 쉬운 형태로 요약합니다.
	 *
	 * @param scores 페르소나별 점수
	 * @return 분석 결과 요약 문자열
	 */
	public String summarizeAnalysis(Map<PersonaType, Integer> scores) {
		return scores.entrySet().stream()
			.filter(entry -> entry.getValue() > 0)
			.sorted(Map.Entry.<PersonaType, Integer>comparingByValue().reversed())
			.map(entry -> String.format("%s: %d점", entry.getKey().getDisplayName(), entry.getValue()))
			.collect(Collectors.joining(", "));
	}
}
