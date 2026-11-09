package com.tienda.plataforma.shared.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

/** Stateless: auth/webhooks y storefront público; roles por superficie + tenant para las rutas privadas. */
@Configuration
public class SecurityConfig {

	private final JwtFilter jwtFilter;
	private final TenantFilter tenantFilter;
	private final TenantRoleAuthorizationManager rolesTienda;
	private final List<String> origenes;

	public SecurityConfig(JwtFilter jwtFilter, TenantFilter tenantFilter,
			TenantRoleAuthorizationManager rolesTienda,
			@Value("${app.cors.origenes:}") String origenes) {
		this.jwtFilter = jwtFilter;
		this.tenantFilter = tenantFilter;
		this.rolesTienda = rolesTienda;
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
		config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Tienda-Slug", "Idempotency-Key",
				"X-Carrito-Token", "Stripe-Signature", "User-Agent", "X-Forwarded-For"));
		config.setAllowCredentials(true);
		config.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", config);
		fuente.registerCorsConfiguration("/auth/**", config);
		return fuente;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)
				.cors(Customizer.withDefaults())
				.exceptionHandling(e -> e
						.accessDeniedHandler((request, response, ex) -> denegar(request, response))
						.authenticationEntryPoint((request, response, ex) -> denegar(request, response)))
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
						.requestMatchers("/auth/tiendas").authenticated()
						.requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**", "/api/webhooks/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/storefront/*/productos",
								"/api/storefront/*/productos/**", "/api/storefront/*/colecciones",
								"/api/storefront/*/colecciones/**", "/api/storefront/*/etiquetas").permitAll()
						.requestMatchers("/api/storefront/*/carrito", "/api/storefront/*/carrito/**").permitAll()
						.requestMatchers("/api/ops/**").hasAuthority("ROLE_super_admin")
						.requestMatchers("/api/admin/usuarios", "/api/admin/usuarios/**",
								"/api/admin/pasarelas", "/api/admin/pasarelas/**",
								"/api/admin/facturacion", "/api/admin/facturacion/**")
							.access(rolesTienda.adminDeTienda())
						.requestMatchers("/api/admin/**").access(rolesTienda.adminOOperadorDeTienda())
						.requestMatchers("/api/checkout/**",
								"/api/storefront/*/cuenta", "/api/storefront/*/cuenta/**")
							.access(rolesTienda.clienteDeTienda())
						.requestMatchers("/api/storefront/**").denyAll()
						.anyRequest().authenticated())
				.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(tenantFilter, JwtFilter.class);
		return http.build();
	}

	/**
	 * RNF-SEG-05: `/api/ops` no revela su existencia (falso 404); el resto
	 * conserva el 403 por defecto de Spring Security.
	 */
	private void denegar(jakarta.servlet.http.HttpServletRequest request,
			jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
		String uri = request.getRequestURI();
		String contexto = request.getContextPath();
		String ruta = contexto == null || contexto.isEmpty() ? uri : uri.substring(contexto.length());
		if (ruta.equals("/api/ops") || ruta.startsWith("/api/ops/")) {
			response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND);
			response.setContentType("application/json");
			response.getWriter().write("{\"codigo\":404,\"mensaje\":\"no encontrado\"}");
			return;
		}
		response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
	}
}
