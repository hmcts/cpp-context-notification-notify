package uk.gov.moj.cpp.notification.notify.paas;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

public class EnvironmentHelperTest {
    
    @Test
    public void shouldReturnNullWhenVaultUrlNotSet() {
        final String result = EnvironmentHelper.getVaultUrl();

        assertThat(result, is(nullValue()));
    }

}

