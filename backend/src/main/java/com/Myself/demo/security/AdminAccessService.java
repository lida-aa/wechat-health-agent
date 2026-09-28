package com.Myself.demo.security;

import com.Myself.demo.config.JwtUtil;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminAccessService {

    public boolean isAdmin(String authorization) {
        Map<String, Object> claims = JwtUtil.verify(bearerToken(authorization));
        return claims != null && "admin".equals(claims.get("role"));
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring(7).trim();
    }
}
