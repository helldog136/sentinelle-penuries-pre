package vd.affsn.sentinelle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SentinelleApplicationSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextStartsWithTestProfile() {
        assertThat(context).isNotNull();
        assertThat(context.getEnvironment().acceptsProfiles(Profiles.of("test"))).isTrue();
    }
}
