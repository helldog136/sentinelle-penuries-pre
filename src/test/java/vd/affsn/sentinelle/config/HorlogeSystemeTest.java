package vd.affsn.sentinelle.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HorlogeSystemeTest {

    private static final Instant T = Instant.parse("2026-03-31T23:30:00Z");

    @Test
    void fuseauParDefautEstUtc() {
        assertThat(HorlogeSysteme.pourFuseau(null)).isNotNull();
        assertThat(HorlogeSysteme.pourFuseau("  ")).isNotNull();
        HorlogeSysteme h = HorlogeSysteme.pourFuseau("UTC");
        assertThat(h.aujourdhui()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    void fuseauInconnuEstRefuseSansEchoDeLaValeur() {
        assertThatThrownBy(() -> HorlogeSysteme.pourFuseau("Mars/Olympus-secret"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sentinelle.horloge.fuseau")
                .hasMessageNotContaining("Olympus");
    }

    @Test
    void maintenantEtAujourdhuiSontCoherentsAvecUnClockFixe() {
        HorlogeSysteme utc = new HorlogeSysteme(Clock.fixed(T, ZoneOffset.UTC));
        HorlogeSysteme tokyo = new HorlogeSysteme(Clock.fixed(T, ZoneId.of("Asia/Tokyo")));
        assertThat(utc.maintenant()).isEqualTo(T);
        assertThat(utc.aujourdhui()).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(tokyo.maintenant()).isEqualTo(T);
        assertThat(tokyo.aujourdhui()).isEqualTo(LocalDate.of(2026, 4, 1));
    }
}
