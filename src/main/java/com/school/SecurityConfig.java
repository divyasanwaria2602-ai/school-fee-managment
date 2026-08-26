package com.school;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import java.util.List;

@Configuration @EnableMethodSecurity class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean UserDetailsService users(UserRepository users) { return username -> users.findByUsernameAndActiveTrue(username)
      .map(u -> User.withUsername(u.username).password(u.passwordHash).authorities(List.of(new SimpleGrantedAuthority("ROLE_"+u.role.name()))).build())
      .orElseThrow(() -> new UsernameNotFoundException(username)); }
  @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.csrf(c->c.disable()).httpBasic(b->{}).formLogin(f->f.disable())
      .authorizeHttpRequests(a->a.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated()).build(); }
}

