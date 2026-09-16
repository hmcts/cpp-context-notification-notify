package uk.gov.moj.cpp.notification.notify.command.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;

/**
 * BC-20 guard (J17 -> J25 parity, see /J25-PARITY-FINDINGS.md). Asserts the access-control kbase compiled
 * at least one rule, so a silent zero-rule load fails loudly rather than making deny-tests pass vacuously.
 */
public class AccessControlRuleCountTest {

    @Test
    public void commandApiKieBaseShouldCompileAtLeastOneRule() {
        final long ruleCount = KieServices.get().getKieClasspathContainer()
                .getKieBase("COMMAND_API")
                .getKiePackages().stream()
                .mapToLong(kiePackage -> kiePackage.getRules().size())
                .sum();

        assertTrue(ruleCount > 0,
                "COMMAND_API kbase compiled 0 rules — access-control deny-tests would pass vacuously (BC-20)");
    }
}
