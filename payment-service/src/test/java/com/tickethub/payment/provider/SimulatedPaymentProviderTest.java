package com.tickethub.payment.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tickethub.payment.common.enums.CurrencyCode;
import com.tickethub.payment.config.SimulatorProperties;
import com.tickethub.payment.enums.ProviderStatus;
import com.tickethub.payment.enums.SimulatedOutcome;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** S5 (idempotent refund) and S9 (lookup reveals the truth after a timeout). */
class SimulatedPaymentProviderTest {

    private SimulatedPaymentProvider provider() {
        SimulatorProperties props = new SimulatorProperties();
        props.setAllowClientOutcome(true);
        props.setHangMs(0); // do not really hang in a unit test
        return new SimulatedPaymentProvider(props);
    }

    @Test
    void refundingTwiceReturnsTheSameRefund() {
        SimulatedPaymentProvider p = provider();
        UUID id = UUID.randomUUID();
        String first = p.refund(id, "SIM-1", new BigDecimal("10.00"));
        String second = p.refund(id, "SIM-1", new BigDecimal("10.00"));
        assertEquals(first, second);
    }

    @Test
    void lookupRevealsThePaymentEvenWhenTheCallerTimedOut() {
        SimulatedPaymentProvider p = provider();
        UUID id = UUID.randomUUID();
        p.initiate(new ProviderRequest(id, new BigDecimal("10.00"), CurrencyCode.INR, SimulatedOutcome.TIMEOUT));
        assertEquals(ProviderStatus.SUCCEEDED, p.lookup(id).getStatus());
    }

    @Test
    void lookupOfAnUnknownPaymentIsNull() {
        assertNull(provider().lookup(UUID.randomUUID()));
    }
}
