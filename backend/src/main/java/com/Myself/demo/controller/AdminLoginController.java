package com.Myself.demo.controller;

import com.Myself.demo.config.JwtUtil;
import com.Myself.demo.mapper.AdminMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminLoginController {

    private final AdminMapper adminMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminLoginController(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Map.of("code", 400, "msg", "请输入用户名和密码");
        }

        String storedPassword = adminMapper.getAdminPassword(username.trim());
        if (storedPassword == null || !storedPassword.startsWith("$2")
                || !passwordEncoder.matches(password, storedPassword)) {
            return Map.of("code", 401, "msg", "用户名或密码错误");
        }
        return Map.of("code", 200, "token", JwtUtil.createToken(username.trim(), "admin"));
    }
}
