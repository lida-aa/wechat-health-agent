package com.Myself.demo.controller;

import com.Myself.demo.entity.UserInfo;
import com.Myself.demo.entity.UserMessage;
import com.Myself.demo.mapper.AdminMapper;
import com.Myself.demo.security.AdminAccessService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private static final int PAGE_SIZE = 50;

    private final AdminMapper adminMapper;
    private final AdminAccessService accessService;

    public AdminUserController(AdminMapper adminMapper, AdminAccessService accessService) {
        this.adminMapper = adminMapper;
        this.accessService = accessService;
    }

    @GetMapping
    public Map<String, Object> listUsers(@RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        List<UserInfo> users = adminMapper.listUsers();
        List<UserInfo> safeUsers = users == null ? List.of() : users.stream()
                .filter(user -> user != null && user.getFromUserId() != null)
                .toList();
        return Map.of("code", 200, "data", safeUsers);
    }

    @GetMapping("/{userId}/messages")
    public Map<String, Object> messages(@PathVariable String userId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        int safePage = Math.max(page, 0);
        List<UserMessage> messages = adminMapper.getMessages(userId, safePage * PAGE_SIZE, PAGE_SIZE);
        return Map.of("code", 200, "data", messages == null ? List.of() : messages);
    }

    @DeleteMapping("/{userId}/messages")
    public Map<String, Object> clearMessages(@PathVariable String userId,
                                             @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        adminMapper.deleteMessages(userId);
        return Map.of("code", 200, "msg", "已清空");
    }

    @PostMapping("/{userId}/messages/batch")
    public Map<String, Object> deleteMessages(@PathVariable String userId,
                                              @RequestBody Map<String, Object> body,
                                              @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        List<Long> ids = parseIds(body.get("ids"));
        if (ids.isEmpty()) {
            return Map.of("code", 400, "msg", "请选择要删除的消息");
        }
        int deleted = adminMapper.deleteMessagesBatch(userId, ids);
        return Map.of("code", 200, "msg", "已删除 " + deleted + " 条消息");
    }

    @PutMapping("/{userId}/nickname")
    public Map<String, Object> updateNickname(@PathVariable String userId,
                                              @RequestBody Map<String, String> body,
                                              @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            return Map.of("code", 400, "msg", "昵称不能为空");
        }
        adminMapper.updateNickname(userId, nickname.trim());
        return Map.of("code", 200, "msg", "已更新");
    }

    @DeleteMapping("/{userId}")
    public Map<String, Object> deleteUser(@PathVariable String userId,
                                          @RequestHeader("Authorization") String authorization) {
        if (!accessService.isAdmin(authorization)) {
            return forbidden();
        }
        adminMapper.deleteMessages(userId);
        adminMapper.deleteUser(userId);
        return Map.of("code", 200, "msg", "已删除");
    }

    private List<Long> parseIds(Object rawIds) {
        if (!(rawIds instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::longValue)
                .filter(id -> id > 0)
                .distinct()
                .toList();
    }

    private Map<String, Object> forbidden() {
        return Map.of("code", 403, "msg", "仅管理员可访问");
    }
}
