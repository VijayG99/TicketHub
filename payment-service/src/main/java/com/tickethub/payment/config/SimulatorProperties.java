package com.tickethub.payment.config;

import com.tickethub.payment.enums.SimulatedOutcome;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "payment.simulator")
public class SimulatorProperties {
    private SimulatedOutcome defaultOutcome = SimulatedOutcome.SUCCESS;
    /** Injected latency for every simulated provider call (ST-16). */
    private long latencyMs = 0;
    /** How long a TIMEOUT outcome hangs. */
    private long hangMs = 30000;
    /** Allow the caller to choose the simulated outcome. Must be false in any real environment. */
    private boolean allowClientOutcome = false;
}
