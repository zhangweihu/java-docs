# 第十三章 Spring Security 安全框架

> 本章掌握：认证与授权概念、Spring Security 过滤链原理、基于数据库的用户认证、RBAC 权限控制、JWT 无状态登录。
> 安全是任何 Web 应用的底线。Spring Security 是 Java 生态**事实标准**的安全框架，登录、权限、防攻击全交给它。

## 13.1 认证与授权（基础概念）

### 13.1.1 两个核心概念

- **认证（Authentication）**：你是谁？—— 登录，验证用户名密码
- **授权（Authorization）**：你能做什么？—— 权限校验，如"只有管理员能删除"

```
请求 ──► 认证：校验身份（用户名/密码/JWT）──► 授权：校验权限（角色/资源）──► 放行
```

### 13.1.2 认证方式演进

| 方式 | 原理 | 优缺点 |
| --- | --- | --- |
| Session（传统） | 登录成功，服务端存 session，返回 cookie | 简单；但**有状态**，集群要共享 session |
| **Token / JWT（现代）** | 登录成功，签发签名 token，客户端每次携带 | **无状态**，适合前后端分离/分布式；token 无法主动失效 |
| OAuth2 / SSO | 第三方授权登录（微信/QQ）、单点登录 | 企业级，体系复杂 |

> 前后端分离项目**主流方案**：JWT。本章以此为主。

### 13.1.3 Spring Security 原理：过滤器链

Spring Security 的本质是一串**过滤器（Filter）**，请求按顺序经过，任何一环不通过就拦截：

```
请求
 │
 ▼
SecurityContextPersistenceFilter   ← 从请求恢复登录用户上下文
 ▼
UsernamePasswordAuthenticationFilter ← 处理用户名/密码登录（form 表单）
 ▼
JwtAuthenticationFilter            ← （自定义）解析 token，设置登录用户
 ▼
FilterSecurityInterceptor          ← 授权校验：访问的资源需要什么权限
 ▼
Controller
```

**核心对象**：
- `SecurityContext`：当前登录用户信息容器
- `Authentication`：认证信息（principal=用户，authorities=权限集合）
- `SecurityFilterChain`：过滤链配置（新版 `WebSecurityConfigurerAdapter` 已废弃，用配置类 + Bean）

## 13.2 快速开始

### 13.2.1 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### 13.2.2 默认效果

添加依赖后什么都不配置，直接启动：
- 访问任意接口 → **跳转到内置登录页** `/login`
- 控制台启动日志会打印一个随机密码：

```
Using generated security password: a1b2c3d4...
```

- 默认用户名 `user`，密码是上面那串随机密码

> 这就是"开箱即用"：所有接口默认全部需要登录。但默认的 form 登录显然不够用，下面开始配置。

### 13.2.3 自定义 SecurityFilterChain（新版写法）

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity   // 开启 Spring Security（新版可省略）
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF（前后端分离/无状态接口不需要，否则 POST 会被拦截）
            .csrf(csrf -> csrf.disable())

            // 授权规则
            .authorizeHttpRequests(auth -> auth
                    // 这些路径放行（无需登录）
                    .requestMatchers("/api/public/**", "/code/**").permitAll()
                    // 其余全部需要认证
                    .anyRequest().authenticated()
            )

            // 认证方式：HTTP Basic（简单演示，后面换 JWT）
            .httpBasic(httpBasic -> {});

        return http.build();
    }
}
```

测试：
- `GET /api/public/hello` → 直接访问（放行）
- `GET /users` → 401，需要认证

## 13.3 基于数据库的用户认证

### 13.3.1 密码加密：BCrypt

**绝不存明文密码**。BCrypt 是自适应哈希算法，自带随机盐、抗彩虹表，是业界标准：

```java
package com.example.demo.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordDemo {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String raw = "123456";
        String hash1 = encoder.encode(raw);   // 每次结果都不同（内部带随机盐）
        String hash2 = encoder.encode(raw);

        System.out.println("hash1 = " + hash1);
        System.out.println("hash2 = " + hash2);
        System.out.println("hash1.equals(hash2) = " + hash1.equals(hash2));  // false

        // 校验：不能 equals 比较，必须用 matches
        System.out.println(encoder.matches(raw, hash1));    // true
        System.out.println(encoder.matches("12345", hash1)); // false
    }
}
```

> 密码正确性用 `matches(明文, 哈希)` 判断，因为每次生成的哈希不同，不能直接比较。

### 13.3.2 PasswordEncoder Bean

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // 注册为 Bean，全局注入使用
    }
}
```

### 13.3.3 UserDetailsService：从数据库查用户

Spring Security 通过 `UserDetailsService.loadUserByUsername()` 加载用户：

```java
package com.example.demo.security;

import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告诉 Spring Security 如何按用户名查用户（对接数据库）
 */
@Service
public class MyUserDetailsService implements UserDetailsService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // ① 查数据库
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }

        // ② 组装权限列表（从角色表查出，简单演示直接给角色）
        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))   // 角色加 ROLE_ 前缀
                .toList();

        // ③ 返回 Spring Security 认识的 UserDetails 对象
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())      // 用户名
                .password(user.getPassword())          // 密码（必须存的是 BCrypt 哈希）
                .authorities(authorities)              // 权限集合
                .build();
    }
}
```

### 13.3.4 登录接口（AuthenticationManager）

```java
package com.example.demo.controller;

import com.example.demo.dto.LoginDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;   // 需要暴露为 Bean，见 13.3.5

    @PostMapping("/login")
    public String login(@RequestBody LoginDTO login) {
        // ① 交给 AuthenticationManager 校验用户名密码（内部调用 UserDetailsService）
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(login.getUsername(), login.getPassword()));

        // ② 认证通过，返回用户信息（13.4 会改成返回 JWT）
        return "登录成功：" + auth.getName();
    }
}
```

### 13.3.5 暴露 AuthenticationManager

```java
// SecurityConfig 中追加：
@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

## 13.4 授权：RBAC 角色权限

### 13.4.1 基于 URL 的授权

```java
package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    // 放行：登录、注册、公开接口
                    .requestMatchers("/login", "/register", "/api/public/**", "/code/**").permitAll()

                    // 按角色：只有 ADMIN 能访问管理接口
                    .requestMatchers("/admin/**").hasRole("ADMIN")

                    // 按权限：只有特定权限能操作
                    .requestMatchers(HttpMethod.POST, "/users/**").hasAuthority("user:add")
                    .requestMatchers(HttpMethod.DELETE, "/users/**").hasAuthority("user:delete")

                    // 其余登录即可
                    .anyRequest().authenticated()
            )
            .httpBasic(httpBasic -> {});
        return http.build();
    }
}
```

| 方法 | 含义 |
| --- | --- |
| `permitAll()` | 无需登录 |
| `authenticated()` | 登录即可 |
| `hasRole("ADMIN")` | 拥有角色（自动匹配 `ROLE_ADMIN`） |
| `hasAuthority("user:add")` | 拥有权限（精细到操作） |
| `hasAnyRole("ADMIN","USER")` | 拥有任一角色 |

### 13.4.2 方法级授权：@PreAuthorize

```java
package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity   // 开启方法级注解 @PreAuthorize / @Secured
public class MethodSecurityConfig {
}
```

```java
package com.example.demo.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class UserAdminService {

    // 只有 ADMIN 角色能调用
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long id) {
        System.out.println("删除用户：" + id);
    }

    // 登录用户才能调用（表达式里可引用方法参数）
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public void viewUser(Long id) {
        System.out.println("查看用户：" + id);
    }
}
```

## 13.5 JWT 认证（前后端分离主流）

### 13.5.1 JWT 结构

JWT（JSON Web Token）由三段组成，用 `.` 连接：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ6aGFuZ3NhbiIsInJvbGUiOiJBRE1JTiJ9.8xYp...签名
└──────┬──────┘ └─────────┬─────────┘ └─────────┬────────┘
   Header(头)        Payload(载荷)          Signature(签名)
```

- **Header**：加密算法（HS256）
- **Payload**：用户信息（不能放敏感数据，可被解码）
- **Signature**：`HMACSHA256(header.payload, 密钥)` —— **防篡改**的关键

**流程**：

```
登录 ──► 校验密码 ──► 签发 JWT（含用户 + 角色 + 过期时间）──► 返回前端
前端每次请求在 Authorization: Bearer <token> 头带上
服务端解析 token → 验签 → 取用户/权限 → 放行（无状态，不需要 Session）
```

### 13.5.2 依赖与配置

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

```yaml
# application.yml
jwt:
  secret: demo-secret-key-please-change-in-production-0123456789   # 至少 32 字节
  expire-minutes: 120
```

### 13.5.3 JWT 工具类

```java
package com.example.demo.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-minutes}")
    private long expireMinutes;

    // 生成密钥
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 签发 token：放入用户名 + 角色
    public String createToken(String username, String role) {
        return Jwts.builder()
                .subject(username)                            // 主体：用户名
                .claim("role", role)                          // 自定义载荷：角色
                .issuedAt(new Date())                         // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expireMinutes * 60 * 1000))
                .signWith(key())                              // 签名
                .compact();
    }

    // 解析 token：验签 + 取出载荷（失败抛异常）
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // 从 token 取用户名
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }
}
```

### 13.5.4 JWT 认证过滤器

每个请求进来，从 `Authorization` 头取 token，解析成功则把用户放进 `SecurityContext`：

```java
package com.example.demo.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 自定义过滤器：解析 JWT，把登录用户放入 SecurityContext
 * 在 Spring Security 过滤链中位于授权校验之前
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // ① 从请求头取 token：Authorization: Bearer xxx
        String header = request.getHeader("Authorization");
        String token = null;
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        // ② 有 token 且当前没有登录用户，才解析
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parseToken(token);
                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                // ③ 组装 Authentication 放入上下文（后续 @PreAuthorize 就能拿到角色）
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // token 无效/过期：不设置认证，走匿名访问，后续被授权规则拦截
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }
}
```

### 13.5.5 SecurityConfig 挂载 JWT 过滤器（无状态）

```java
package com.example.demo.config;

import com.example.demo.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF（JWT 无状态，不需要）
            .csrf(csrf -> csrf.disable())

            // 无状态：不创建 Session（前后端分离核心配置）
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/login", "/register", "/api/public/**").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            )

            // 在用户名密码过滤器之后挂上 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

### 13.5.6 登录接口返回 JWT

```java
package com.example.demo.controller;

import com.example.demo.dto.LoginDTO;
import com.example.demo.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginDTO login) {
        // ① 校验用户名密码（失败抛 BadCredentialsException → 全局异常处理）
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(login.getUsername(), login.getPassword()));

        // ② 取角色（第一个权限作为角色，简单演示）
        String role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("USER")
                .replace("ROLE_", "");

        // ③ 签发 JWT
        String token = jwtUtil.createToken(login.getUsername(), role);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("username", login.getUsername());
        result.put("role", role);
        return result;
    }

    // 测试：登录后才能访问
    @org.springframework.web.bind.annotation.GetMapping("/me")
    public Map<String, String> me() {
        Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
        Map<String, String> map = new HashMap<>();
        map.put("username", auth.getName());
        return map;
    }
}
```

**完整测试流程（Postman / curl）：**

```bash
# ① 登录拿 token
curl -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"admin\",\"password\":\"123456\"}"
# → {"token":"eyJhbGciOi...","username":"admin","role":"ADMIN"}

# ② 携带 token 访问受保护接口
curl http://localhost:8080/users -H "Authorization: Bearer eyJhbGciOi..."

# ③ 不带 token → 401
curl http://localhost:8080/users
```

## 13.6 未登录/无权限返回 JSON（前后端分离）

默认 Spring Security 对未认证返回 302 跳转登录页、403 返回空白页。前后端分离需要自定义返回 JSON：

```java
package com.example.demo.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class SecurityJsonConfig {

    @Bean
    public SecurityFilterChain jsonSecurityFilterChain(HttpSecurity http) throws Exception {
        // 未登录：返回 401 JSON
        http.exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) ->
                        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "未登录或登录已过期"))
                // 无权限：返回 403 JSON
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        writeJson(response, HttpServletResponse.SC_FORBIDDEN, "没有访问权限"))
        );
        return http.build();
    }

    private void writeJson(HttpServletResponse response, int status, String msg) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", status);
        body.put("message", msg);
        response.getWriter().write(new ObjectMapper().writeValueAsString(body));
    }
}
```

## 13.7 小结与练习

**本章重点**：
- 认证（你是谁）与授权（你能干什么）
- Spring Security 本质是过滤器链
- `UserDetailsService` 对接数据库 + BCrypt 密码加密
- RBAC：URL 级（`hasRole`）+ 方法级（`@PreAuthorize`）
- JWT 无状态认证：`JwtUtil` 签发/解析 + 自定义过滤器挂载 + 无状态 Session

**面试题参考**：
1. Spring Security 的认证流程？（过滤器链 → AuthenticationManager → UserDetailsService）
2. 为什么密码用 BCrypt？BCrypt 的特点？
3. Session 认证与 JWT 认证的区别？
4. JWT 的缺点？（无法主动失效、payload 明文可解码）
5. 如何实现记住我/退出登录？（token 黑名单或 Redis 白名单）

**课后练习**：
1. 新建 Spring Boot 项目引入 Security，配置放行 `/api/public/**`，验证其余接口 401。
2. 实现注册接口：BCrypt 加密存库 + 登录校验（UserDetailsService 查询）。
3. 配置角色：`/admin/**` 仅 ADMIN 可访问，注册两个用户（admin/user）验证 403。
4. 接入 JWT：登录返回 token，写一个 `@PreAuthorize("hasRole('ADMIN')")` 接口验证。
5. 改造第九章用户项目：所有接口需登录，管理接口仅管理员。

上一章：[12-Redis.md](./12-Redis.md) | 下一章：[14-设计模式.md](./14-设计模式.md) | 返回目录：[README.md](./README.md)
