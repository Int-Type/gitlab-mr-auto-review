package com.inttype.codereview.review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
	// @Bean
	// SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
	// 	http.csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/gitlab"))
	// 		.authorizeHttpRequests(auth -> auth
	// 			.requestMatchers("/webhooks/gitlab").permitAll()
	// 			.anyRequest().denyAll())
	// 		.httpBasic(Customizer.withDefaults());
	// 	return http.build();
	// }
	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.headers(h -> h.frameOptions(f -> f.deny()))
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/actuator/health", "/actuator/info").permitAll()
				.requestMatchers(HttpMethod.POST, "/webhooks/gitlab").permitAll()
				.anyRequest().authenticated()
			)
			.httpBasic(Customizer.withDefaults());
		return http.build();
	}
}
