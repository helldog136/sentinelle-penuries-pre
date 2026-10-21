package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;

/**
 * Validation des paramètres du job : seuls {@code dateReference} (texte AAAA-MM-JJ, date réelle)
 * et {@code run.id} sont acceptés ; les messages ne recopient jamais la valeur reçue.
 */
class DateReferenceJobParametersValidatorTest {

    private final DateReferenceJobParametersValidator validateur = new DateReferenceJobParametersValidator();

    private static JobParameters date(String valeur) {
        return new JobParametersBuilder().addString("dateReference", valeur).toJobParameters();
    }

    @Nested
    class Acceptes {

        @Test
        void dateSeuleAcceptee() {
            assertThatCode(() -> validateur.validate(date("2026-10-21"))).doesNotThrowAnyException();
        }

        @Test
        void dateEtRunIdAcceptes() {
            JobParameters p = new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21")
                    .addLong("run.id", 42L)
                    .toJobParameters();
            assertThatCode(() -> validateur.validate(p)).doesNotThrowAnyException();
        }

        @Test
        void dateNonIdentifianteAcceptee() {
            JobParameters p = new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21", false)
                    .toJobParameters();
            assertThatCode(() -> validateur.validate(p)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @ValueSource(strings = {"2028-02-29", "0000-01-01", "9999-12-31", "2026-01-01", "2026-12-31"})
        void bornesEtAnneeBissextileAcceptees(String valeur) {
            assertThatCode(() -> validateur.validate(date(valeur))).doesNotThrowAnyException();
        }

        @Test
        void constantesPubliques() {
            assertThat(DateReferenceJobParametersValidator.DATE_REFERENCE).isEqualTo("dateReference");
            assertThat(DateReferenceJobParametersValidator.RUN_ID).isEqualTo("run.id");
        }
    }

    @Nested
    class Refuses {

        @Test
        void parametresNulsRefuses() {
            assertThatThrownBy(() -> validateur.validate(null))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("Paramètres de job absents");
        }

        @Test
        void dateAbsenteRefusee() {
            assertThatThrownBy(() -> validateur.validate(new JobParameters()))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessageContaining("dateReference obligatoire");
        }

        @Test
        void runIdSeulRefuse() {
            JobParameters p = new JobParametersBuilder().addLong("run.id", 1L).toJobParameters();
            assertThatThrownBy(() -> validateur.validate(p))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessageContaining("dateReference obligatoire");
        }

        @Test
        void dateDeTypeNonTexteRefusee() {
            // Un LocalDate typé n'est pas le contrat (texte AAAA-MM-JJ) : refusé.
            JobParameters p = new JobParametersBuilder()
                    .addLocalDate("dateReference", LocalDate.of(2026, 10, 21))
                    .toJobParameters();
            assertThatThrownBy(() -> validateur.validate(p))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessageContaining("texte AAAA-MM-JJ");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "2026-10-21 ", " 2026-10-21", "2026-1-21", "2026-10-1", "26-10-21",
            "+2026-10-21", "-2026-10-21", "12026-10-21", "2026/10/21", "21-10-2026", "2026-10-21T00:00",
            "20261021", "aaaa-mm-jj", "../../etc/passwd", "2026-10-21\n",
            // Chiffres non ASCII (arabes orientaux) : \d n'accepte que [0-9].
            "٢٠٢٦-١٠-٢١"})
        void formatInvalideRefuse(String valeur) {
            assertThatThrownBy(() -> validateur.validate(date(valeur)))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("dateReference : format AAAA-MM-JJ attendu");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-02-30", "2026-02-29", "2026-13-01", "2026-00-10", "2026-04-31", "2026-10-00",
            "2026-10-32"})
        void dateCalendaireInexistanteRefusee(String valeur) {
            assertThatThrownBy(() -> validateur.validate(date(valeur)))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("dateReference : date calendaire invalide");
        }

        @Test
        void parametreInconnuRefuseMemeAvecDateValide() {
            JobParameters p = new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21")
                    .addString("seuil", "0")
                    .toJobParameters();
            assertThatThrownBy(() -> validateur.validate(p))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("Paramètre de job non autorisé");
        }

        @Test
        void parametreInconnuNonIdentifiantRefuseAussi() {
            JobParameters p = new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21")
                    .addString("commentaire", "x", false)
                    .toJobParameters();
            assertThatThrownBy(() -> validateur.validate(p))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("Paramètre de job non autorisé");
        }

        @Test
        void casseDesClesRespectee() {
            JobParameters p = new JobParametersBuilder().addString("DATEREFERENCE", "2026-10-21").toJobParameters();
            assertThatThrownBy(() -> validateur.validate(p))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("Paramètre de job non autorisé");
        }
    }

    @Nested
    class MessagesSansValeurRecue {

        private static final String MARQUEUR = "<script>alert('x')</script>";

        @Test
        void formatInvalideNeRecopiePasLaValeur() {
            assertThatThrownBy(() -> validateur.validate(date("2026-10-21" + MARQUEUR)))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(MARQUEUR).doesNotContain("2026-10-21"));
        }

        @Test
        void dateInexistanteNeRecopiePasLaValeurNiLaCause() {
            assertThatThrownBy(() -> validateur.validate(date("2026-02-30")))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .satisfies(e -> {
                        assertThat(e.getMessage()).doesNotContain("2026-02-30");
                        // La DateTimeParseException (qui contient la valeur) n'est pas chaînée.
                        assertThat(e.getCause()).isNull();
                    });
        }

        @Test
        void cleInconnueNonRecopiee() {
            JobParameters p = new JobParametersBuilder()
                    .addString("dateReference", "2026-10-21")
                    .addString(MARQUEUR, "valeur-secrete")
                    .toJobParameters();
            assertThatThrownBy(() -> validateur.validate(p))
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(MARQUEUR).doesNotContain("valeur-secrete"));
        }
    }

    @Nested
    class AnalyseStricte {

        @Test
        void analyseRenvoieLaDate() throws Exception {
            assertThat(DateReferenceJobParametersValidator.analyser("2026-10-21")).isEqualTo(LocalDate.of(2026, 10, 21));
            assertThat(DateReferenceJobParametersValidator.analyser("0000-01-01")).isEqualTo(LocalDate.of(0, 1, 1));
        }

        @Test
        void analyseDeNullRefusee() {
            assertThatThrownBy(() -> DateReferenceJobParametersValidator.analyser(null))
                    .isInstanceOf(JobParametersInvalidException.class)
                    .hasMessage("dateReference : format AAAA-MM-JJ attendu");
        }
    }
}
