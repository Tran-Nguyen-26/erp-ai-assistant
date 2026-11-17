package vn.edu.uet.erpai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import vn.edu.uet.erpai.common.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, JwtAuthenticationFilter jwtFilter, ObjectMapper mapper) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR)
                    .permitAll() // hoàn tất SSE/lỗi; quyền đã kiểm tra ở lần dispatch đầu
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh")
                    .permitAll()
                    .requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (req, res, ex) ->
                            writeProblem(
                                mapper,
                                req.getRequestURI(),
                                res,
                                HttpStatus.UNAUTHORIZED,
                                "AUTH_UNAUTHORIZED",
                                "Thiếu hoặc sai token"))
                    .accessDeniedHandler(
                        (req, res, ex) ->
                            writeProblem(
                                mapper,
                                req.getRequestURI(),
                                res,
                                HttpStatus.FORBIDDEN,
                                "AUTH_FORBIDDEN",
                                "Không đủ quyền")))
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private static void writeProblem(
      ObjectMapper mapper,
      String instance,
      HttpServletResponse res,
      HttpStatus status,
      String code,
      String detail)
      throws java.io.IOException {
    res.setStatus(status.value());
    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    res.setCharacterEncoding("UTF-8");
    mapper.writeValue(
        res.getOutputStream(),
        Map.of(
            "type",
            "https://erpai.local/errors/" + code.toLowerCase().replace('_', '-'),
            "title",
            status.getReasonPhrase(),
            "status",
            status.value(),
            "detail",
            detail,
            "instance",
            instance,
            "code",
            code));
  }
}
