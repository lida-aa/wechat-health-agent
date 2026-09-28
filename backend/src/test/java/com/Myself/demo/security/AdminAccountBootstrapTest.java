package com.Myself.demo.security;

import com.Myself.demo.mapper.AdminMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AdminAccountBootstrapTest {

    @Test
    void doesNothingWhenBootstrapCredentialsAreNotConfigured() throws Exception {
        AdminMapper mapper = mock(AdminMapper.class);

        new AdminAccountBootstrap(mapper, "", "").run();

        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsWeakBootstrapPassword() {
        AdminMapper mapper = mock(AdminMapper.class);
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(mapper, "admin", "short");

        assertThatThrownBy(bootstrap::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 characters");
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsPartiallyConfiguredBootstrapCredentials() {
        AdminMapper mapper = mock(AdminMapper.class);

        assertThatThrownBy(() -> new AdminAccountBootstrap(mapper, "health-admin", "").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("configured together");
        verifyNoInteractions(mapper);
    }

    @Test
    void storesConfiguredPasswordAsBcryptHash() throws Exception {
        AdminMapper mapper = mock(AdminMapper.class);
        String password = "a-strong-password";

        new AdminAccountBootstrap(mapper, "health-admin", password).run();

        verify(mapper).upsertAdmin(eq("health-admin"), argThat(hash ->
                hash != null && new BCryptPasswordEncoder().matches(password, hash)));
    }
}
