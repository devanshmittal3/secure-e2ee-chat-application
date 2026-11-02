
package com.secureconnect.backend.scheduler;

import com.secureconnect.backend.service.SessionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionExpiryScheduler {

    private static final Logger logger =
            LoggerFactory.getLogger(SessionExpiryScheduler.class);

    private final SessionService sessionService;

    public SessionExpiryScheduler(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    // Run once every hour.
    @Scheduled(fixedDelay = 60 * 60 * 1000)
    public void checkInactiveSessions() {
        try {
            sessionService.expireInactiveSessions();

            logger.info("Session expiry check completed successfully.");

        } catch (Exception exception) {
            logger.error(
                    "Session expiry check failed.",
                    exception
            );
        }
    }
}
