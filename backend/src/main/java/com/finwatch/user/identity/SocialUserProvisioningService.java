package com.finwatch.user.identity;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.finwatch.auth.kakao.KakaoIdTokenValidator.KakaoProfile;
import com.finwatch.user.domain.AppUser;
import com.finwatch.user.domain.UserStatus;
import com.finwatch.user.repository.AppUserRepository;

@Service
public class SocialUserProvisioningService {

    private final AuthIdentityRepository identityRepository;
    private final AppUserRepository userRepository;
    private final TransactionTemplate transaction;

    public SocialUserProvisioningService(
            AuthIdentityRepository identityRepository,
            AppUserRepository userRepository,
            PlatformTransactionManager transactionManager) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AppUser provisionKakao(KakaoProfile profile) {
        AppUser existing = transaction.execute(status -> updateExisting(profile));
        if (existing != null) {
            return existing;
        }
        try {
            AppUser created = transaction.execute(status -> {
                AppUser user = userRepository.save(AppUser.createSocial(profile.nickname(), profile.picture()));
                identityRepository.saveAndFlush(AuthIdentity.kakao(user, profile.subject(), profile.email()));
                return user;
            });
            if (created == null) {
                throw new IllegalStateException("Kakao user transaction returned no user.");
            }
            return created;
        } catch (DataIntegrityViolationException concurrentCreation) {
            AppUser concurrent = transaction.execute(status -> updateExisting(profile));
            if (concurrent == null) {
                throw concurrentCreation;
            }
            return concurrent;
        }
    }

    private AppUser updateExisting(KakaoProfile profile) {
        var identity = identityRepository
                .findByProviderAndProviderSubject(AuthProvider.KAKAO, profile.subject())
                .orElse(null);
        if (identity == null) {
            return null;
        }
        AppUser user = identity.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("사용할 수 없는 계정입니다.");
        }
        identity.recordLogin(profile.email());
        user.recordLogin(profile.nickname(), profile.picture());
        return user;
    }
}
