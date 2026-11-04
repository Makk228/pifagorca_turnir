package ru.mentalarena;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  org.springframework.security.core.userdetails.UserDetailsService unusedUsers() {
    return name -> {
      throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
          "Use phone login");
    };
  }

  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
        .headers(
            h ->
                h.contentSecurityPolicy(
                    c ->
                        c.policyDirectives(
                            "default-src 'self'; script-src 'self'; style-src 'self'; img-src"
                                + " 'self' data:; connect-src 'self'; frame-ancestors 'none';"
                                + " base-uri 'self'; form-action 'self'")))
        .formLogin(f -> f.disable())
        .httpBasic(b -> b.disable())
        .logout(l -> l.disable())
        .requestCache(c -> c.disable())
        .build();
  }
}
