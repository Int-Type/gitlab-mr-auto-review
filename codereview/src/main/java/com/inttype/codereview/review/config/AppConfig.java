package com.inttype.codereview.review.config;

import org.gitlab4j.api.GitLabApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.RequiredArgsConstructor;

/**
 * 애플리케이션 전체 설정을 관리하는 Configuration 클래스
 *
 * <p>GitLab API 클라이언트와 Configuration Properties를 설정합니다.
 * WebClient는 각 어댑터에서 필요에 따라 개별적으로 생성하여 사용합니다.</p>
 *
 * @author inttype
 * @since 1.0
 */
@Configuration
@EnableAspectJAutoProxy
@RequiredArgsConstructor
@EnableAsync
@EnableConfigurationProperties({GitLabProps.class})
public class AppConfig {

	/**
	 * GitLab API 클라이언트를 생성합니다.
	 *
	 * <p>MR 정보 조회, 댓글 작성 등 GitLab과의 모든 상호작용에 사용됩니다.</p>
	 *
	 * @param props GitLab 연동 설정 정보
	 * @return 설정된 GitLabApi 인스턴스
	 */
	@Bean
	public GitLabApi gitLabApi(GitLabProps props) {
		return new GitLabApi(props.getUrl(), props.getToken());
	}
}
