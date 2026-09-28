package com.Myself.demo.controller;

import com.Myself.demo.config.JwtUtil;
import com.Myself.demo.mapper.AdminMapper;
import com.Myself.demo.security.AdminAccessService;
import com.Myself.demo.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBoundaryTest {

    @Test
    void onlyAdminRolePassesAccessCheck() {
        AdminAccessService accessService = new AdminAccessService();

        assertThat(accessService.isAdmin("Bearer " + JwtUtil.createToken("admin", "admin"))).isTrue();
        assertThat(accessService.isAdmin("Bearer " + JwtUtil.createToken("user-1", "user"))).isFalse();
        assertThat(accessService.isAdmin("Bearer invalid")).isFalse();
        assertThat(accessService.isAdmin(null)).isFalse();
    }

    @Test
    void unauthorizedCallerCannotReadUserList() {
        AdminMapper mapper = mock(AdminMapper.class);
        AdminAccessService accessService = mock(AdminAccessService.class);
        when(accessService.isAdmin("Bearer user-token")).thenReturn(false);
        AdminUserController controller = new AdminUserController(mapper, accessService);

        Map<String, Object> response = controller.listUsers("Bearer user-token");

        assertThat(response.get("code")).isEqualTo(403);
        verifyNoInteractions(mapper);
    }

    @Test
    void unauthorizedCallerCannotUploadKnowledge() {
        RagService ragService = mock(RagService.class);
        AdminAccessService accessService = mock(AdminAccessService.class);
        when(accessService.isAdmin("Bearer user-token")).thenReturn(false);
        HealthKnowledgeController controller = new HealthKnowledgeController(ragService, accessService);
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "private health data".getBytes());

        Map<String, Object> response = controller.upload(file, "user-1", "Bearer user-token");

        assertThat(response.get("code")).isEqualTo(403);
        verifyNoInteractions(ragService);
    }

    @Test
    void knowledgeUploadRejectsGeneralPurposeFileTypes() {
        RagService ragService = mock(RagService.class);
        AdminAccessService accessService = mock(AdminAccessService.class);
        when(accessService.isAdmin("Bearer admin-token")).thenReturn(true);
        HealthKnowledgeController controller = new HealthKnowledgeController(ragService, accessService);
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.exe", "application/octet-stream", new byte[]{1, 2, 3});

        Map<String, Object> response = controller.upload(file, "", "Bearer admin-token");

        assertThat(response.get("code")).isEqualTo(400);
        verifyNoInteractions(ragService);
    }

    @Test
    void adminLoginVerifiesBcryptPasswordInsteadOfComparingPlaintext() {
        AdminMapper mapper = mock(AdminMapper.class);
        when(mapper.getAdminPassword("admin"))
                .thenReturn(new BCryptPasswordEncoder().encode("correct-password"));
        AdminLoginController controller = new AdminLoginController(mapper);

        Map<String, Object> accepted = controller.login(
                Map.of("username", "admin", "password", "correct-password"));
        Map<String, Object> rejected = controller.login(
                Map.of("username", "admin", "password", "wrong-password"));

        assertThat(accepted.get("code")).isEqualTo(200);
        assertThat(rejected.get("code")).isEqualTo(401);
    }
}
