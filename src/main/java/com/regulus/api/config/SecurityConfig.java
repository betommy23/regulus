package com.regulus.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public UserDetailsService users(@Value("${app.security.username}") String username,
                                   @Value("${app.security.password}") String password,
                                   PasswordEncoder encoder) {
        if (username.trim().isEmpty() || password.trim().isEmpty()) {
            throw new IllegalArgumentException("API credentials must not be blank");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("API").build());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests().antMatchers("/error").permitAll()
                .antMatchers("/api/**", "/openapi.yaml").hasRole("API").anyRequest().denyAll().and()
            .formLogin().disable().httpBasic().authenticationEntryPoint((request, response, ex) -> {
                response.setStatus(401);
                response.setHeader("WWW-Authenticate", "Basic realm=\"regulus\"");
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Authentication required\"}");
            }).and().exceptionHandling().accessDeniedHandler((request, response, ex) -> {
                response.setStatus(403);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Access denied\"}");
            });
        return http.build();
    }
}
