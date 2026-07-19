package com.analyzer.api.scheduler;

import com.analyzer.api.entity.User;
import com.analyzer.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ExpertPasswordDeadlineScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ExpertPasswordDeadlineScheduler.class);

    private final UserRepository userRepository;

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void lockExpiredExpertAccounts() {
        List<User> expired = userRepository
                .findAllByMustChangePasswordTrueAndActiveTrueAndPasswordResetDeadlineBefore(LocalDateTime.now());

        if (expired.isEmpty()) {
            return;
        }

        expired.forEach(user -> user.setActive(false));
        userRepository.saveAll(expired);
        logger.info("Locked {} expert account(s) for missing the password-change deadline", expired.size());
    }
}
