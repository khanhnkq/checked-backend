package com.codegym.locketclone.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter
@Setter
public class RateLimitProperties {
    private boolean enabled = true;
    private int registerCapacity = 3;
    private int verifyCapacity = 5;
    private int loginCapacity = 10;
}
