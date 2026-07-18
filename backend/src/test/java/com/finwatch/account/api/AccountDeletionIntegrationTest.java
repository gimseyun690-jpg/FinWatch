package com.finwatch.account.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.finwatch.auth.kakao.KakaoIdTokenValidator.KakaoProfile;
import com.finwatch.auth.session.WebSessionService;
import com.finwatch.user.domain.UserStatus;
import com.finwatch.user.identity.AuthIdentityRepository;
import com.finwatch.user.identity.AuthProvider;
import com.finwatch.user.identity.SocialUserProvisioningService;
import com.finwatch.user.repository.AppUserRepository;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class AccountDeletionIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SocialUserProvisioningService provisioningService;
    @Autowired private WebSessionService sessionService;
    @Autowired private AppUserRepository userRepository;
    @Autowired private AuthIdentityRepository identityRepository;

    @Test
    void deletesSocialIdentityAndAnonymizesUserWithCsrf() throws Exception {
        String subject = "delete-kakao-subject";
        var user = provisioningService.provisionKakao(new KakaoProfile(subject, "삭제 대상", null, null));
        var session = sessionService.create(user, AuthProvider.KAKAO);
        Cookie cookie = new Cookie("FW_SESSION", session.rawSessionId());

        mockMvc.perform(delete("/api/v1/account")
                        .cookie(cookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("계정 삭제 완료"));

        var deleted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(deleted.getEmail()).isNull();
        assertThat(deleted.getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(identityRepository.findByProviderAndProviderSubject(AuthProvider.KAKAO, subject)).isEmpty();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/auth/session").cookie(cookie))
                .andExpect(status().isUnauthorized());
    }
}
