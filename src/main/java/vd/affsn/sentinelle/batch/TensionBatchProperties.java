package vd.affsn.sentinelle.batch;

import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.ZoneId;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration du job nocturne {@code calculerTensionNocturne} (préfixe {@code sentinelle.batch}).
 *
 * <p>Toutes les valeurs sont validées au démarrage : une valeur invalide empêche l'application
 * de démarrer plutôt que de produire un job silencieusement faux.
 *
 * @param enabled       active le job et sa planification (désactivé par défaut)
 * @param cron          expression cron Spring à six champs du lancement planifié
 *                      (défaut {@code 0 0 2 * * *}, soit 02:00 chaque jour)
 * @param fuseau        identifiant de fuseau (ZoneId) dans lequel le cron est interprété
 *                      (défaut {@code UTC})
 * @param seuilTension  seuil de notification : une région est signalée si son indice est
 *                      STRICTEMENT supérieur à cette valeur ; nombre fini {@code >= 0}
 *                      (défaut {@code 10.0}, cf. ADR-003)
 * @param notifications emplacement du fichier de notifications
 */
@Validated
@ConfigurationProperties(prefix = "sentinelle.batch")
public record TensionBatchProperties(
        @DefaultValue("false") boolean enabled,
        @NotBlank @DefaultValue("0 0 2 * * *") String cron,
        @NotBlank @DefaultValue("UTC") String fuseau,
        @NotNull @DecimalMin("0.0") @DefaultValue("10.0") Double seuilTension,
        @NotNull @Valid @DefaultValue Notifications notifications) {

    public TensionBatchProperties {
        if (cron == null || !CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("sentinelle.batch.cron : expression cron invalide");
        }
        try {
            ZoneId.of(fuseau);
        } catch (DateTimeException | NullPointerException e) {
            throw new IllegalArgumentException("sentinelle.batch.fuseau : fuseau horaire invalide", e);
        }
        if (seuilTension == null || !Double.isFinite(seuilTension) || seuilTension < 0) {
            throw new IllegalArgumentException("sentinelle.batch.seuil-tension : nombre fini >= 0 attendu");
        }
    }

    public ZoneId zoneId() {
        return ZoneId.of(fuseau);
    }

    /**
     * @param repertoire répertoire ABSOLU où le fichier de notifications est écrit ; fixé par
     *                   l'exploitation, jamais par un utilisateur. Défaut : sous le répertoire
     *                   temporaire du système ({@code java.io.tmpdir}).
     */
    public record Notifications(@NotNull Path repertoire) {

        public Notifications {
            if (repertoire == null) {
                repertoire = Path.of(System.getProperty("java.io.tmpdir"), "sentinelle", "notifications-tension");
            }
            if (!repertoire.isAbsolute()) {
                throw new IllegalArgumentException(
                        "sentinelle.batch.notifications.repertoire : chemin absolu attendu");
            }
            repertoire = repertoire.normalize();
        }
    }
}
