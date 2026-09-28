package com.Myself.demo.security;

import com.Myself.demo.mapper.AdminMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Order(-100)
@Component
public class AdminAccountBootstrap implements CommandLineRunner {

    private final AdminMapper adminMapper;
    private final String username;
    private final String password;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAccountBootstrap(AdminMapper adminMapper,
                                 @Value("${HEALTH_ADMIN_USERNAME:}") String username,
                                 @Value("${HEALTH_ADMIN_PASSWORD:}") String password) {
        this.adminMapper = adminMapper;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(String... args) {
        if (username.isBlank() != password.isBlank()) {
            throw new IllegalStateException(
                    "HEALTH_ADMIN_USERNAME and HEALTH_ADMIN_PASSWORD must be configured together");
        }
        if (username.isBlank() || password.isBlank()) {
            log.info("未配置健康后台管理员初始化变量，保留数据库中的现有管理员账号");
            return;
        }
        if (password.length() < 12) {
            throw new IllegalStateException("HEALTH_ADMIN_PASSWORD must contain at least 12 characters");
        }
        adminMapper.upsertAdmin(username, passwordEncoder.encode(password));
        log.info("健康后台管理员账号已安全初始化: usernameChars={}", username.length());
    }
}
