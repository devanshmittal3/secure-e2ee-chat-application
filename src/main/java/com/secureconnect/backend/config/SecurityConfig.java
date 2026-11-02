
package com.secureconnect.backend.config;

import com.secureconnect.backend.security.JwtAuthenticationFilter;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS
                )
            )

            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(
                    new HttpStatusEntryPoint(
                        HttpStatus.UNAUTHORIZED
                    )
                )
            )

            .authorizeHttpRequests(auth -> auth

                // Allow Spring error dispatches.
                .dispatcherTypeMatchers(
                    DispatcherType.ERROR
                ).permitAll()

                // Existing public endpoints.
                .requestMatchers(
                    "/api/hello",
                    "/api/auth/register",
                    "/api/auth/login"
                ).permitAll()

                // Phone B can create a pairing request.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/device-link/requests"
                ).permitAll()

                // Phone B can poll using its private polling token.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/device-link/poll"
                ).permitAll()

                // Phone A must be authenticated to approve.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/device-link/approve"
                ).authenticated()

                // All remaining endpoints require authentication.
                .anyRequest().authenticated()
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }

    // Prevent the JWT filter from being registered twice.
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter>
            jwtFilterRegistration() {

        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(
                    jwtAuthenticationFilter
                );

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
