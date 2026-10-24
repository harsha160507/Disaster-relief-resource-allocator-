package com.disasterrelief.allocator.config;

import com.disasterrelief.allocator.service.AdministrationService;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "security.bootstrap", name = "enabled", havingValue = "true")
public class AdministratorBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);
    private final AdministrationService administration;
    private final String subject;
    private final String displayName;

    public AdministratorBootstrap(AdministrationService administration,
            @Value("${security.bootstrap.admin-subject:}") String subject,
            @Value("${security.bootstrap.admin-display-name:}") String displayName) {
        this.administration = administration;
        this.subject = subject;
        this.displayName = displayName;
    }

    @Override public void run(ApplicationArguments args) {
        boolean created = administration.bootstrap(subject, displayName);
        log.info(created ? "Initial system administrator provisioned; disable bootstrap for subsequent starts"
                : "An active system administrator already exists; bootstrap made no changes");
    }
}
