package com.mall.interceptor;

import com.mall.common.BusinessException;
import com.mall.common.ResultCode;
import com.mall.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器：从请求头 Authorization 中解析 Bearer Token，
 * 校验通过后把 userId / username 写入 ThreadLocal（见 UserContext），
 * 后续 Controller / Service 可直接取用。
 */
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String auth = request.getHeader(HEADER);
        if (auth == null || !auth.startsWith(PREFIX)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        Claims claims = jwtUtil.parseToken(auth.substring(PREFIX.length()));
        // 把用户信息放入上下文，供业务层使用
        UserContext.set(claims);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束必须清理，防止 ThreadLocal 在 Tomcat 线程池中复用导致串号
        UserContext.clear();
    }

    /** 当前登录用户上下文（基于 ThreadLocal，单线程内共享） */
    public static class UserContext {
        private static final ThreadLocal<Claims> HOLDER = new ThreadLocal<>();

        public static void set(Claims claims) {
            HOLDER.set(claims);
        }

        public static Long userId() {
            Claims claims = HOLDER.get();
            return claims == null ? null : Long.valueOf(claims.getSubject());
        }

        public static String username() {
            Claims claims = HOLDER.get();
            return claims == null ? null : claims.get("username", String.class);
        }

        public static void clear() {
            HOLDER.remove();
        }
    }
}
