package com.tienda.plataforma.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.Header;
import org.springframework.security.web.header.writers.DelegatingRequestMatcherHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Stateless: /auth/** público; /api/** con JWT (+ tienda vía TenantFilter). */
@Configuration
public class SecurityConfig {

	private final JwtFilter jwtFilter;
	private final TenantFilter tenantFilter;
	private final List<String> origenes;

	public SecurityConfig(JwtFilter jwtFilter, TenantFilter tenantFilter,
			@Value("${app.cors.origenes:}") String origenes) {
		this.jwtFilter = jwtFilter;
		this.tenantFilter = tenantFilter;
		this.origenes = Arrays.stream(origenes.split(",")).map(String::trim)
				.filter(s -> !s.isEmpty()).toList();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/**
	 * RNF-SEG-02: CORS restringido a los orígenes configurados (el SPA),
	 * con credenciales para la cookie de refresh. Sin orígenes = API cerrada
	 * al navegador (solo server-to-server y Postman).
	 */
	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration config = new CorsConfiguration();
		config.setAllowedOrigins(origenes);
		config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tienda-Slug",
				"Stripe-Signature", "User-Agent", "X-Forwarded-For"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", config);
		return fuente;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.headers(h -> h
						.cacheControl(c -> c.disable())
						.addHeaderWriter(new DelegatingRequestMatcherHeaderWriter(
								new OrRequestMatcher(
										PathPatternRequestMatcher.withDefaults().matcher("/api/checkout/**"),
										PathPatternRequestMatcher.withDefaults().matcher("/api/admin/**")),
								new StaticHeadersWriter(List.of(
										new Header("Cache-Control", "no-store"),
										new Header("X-Robots-Tag", "noindex, nofollow"))))))
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/api/webhooks/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(tenantFilter, JwtFilter.class);
		return http.build();
	}
}
