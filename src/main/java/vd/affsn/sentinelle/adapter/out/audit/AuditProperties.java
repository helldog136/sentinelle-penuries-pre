package vd.affsn.sentinelle.adapter.out.audit;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration du journal d'audit (préfixe {@code sentinelle.audit}). Toutes les valeurs ont un
 * défaut sûr : aucune n'est obligatoire et aucune n'est secrète.
 *
 * <ul>
 *   <li>{@code hash-genese} : valeur de {@code hash_precedent} de la toute première entrée de la
 *       chaîne (ADR-003). 64 caractères hexadécimaux minuscules ; défaut : 64 zéros. Ce n'est PAS
 *       un secret : la sécurité du chaînage ne repose pas sur elle. <b>Ne jamais la changer sur un
 *       journal non vide</b> : la vérification d'intégrité signalerait alors une rupture dès la
 *       séquence de tête.</li>
 *   <li>{@code delai-attente-verrou} : attente maximale du verrou d'écriture de la chaîne avant
 *       échec (l'opération métier appelante est alors annulée). Défaut : 10 s ; borné à
 *       ]0 ; 5 min].</li>
 *   <li>{@code taille-page-verification} : nombre de lignes lues par page lors de la
 *       vérification d'intégrité. Défaut : 500 ; borné à [1 ; 10 000].</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "sentinelle.audit")
public record AuditProperties(String hashGenese,
                              Duration delaiAttenteVerrou,
                              Integer taillePageVerification) {

    public static final String HASH_GENESE_PAR_DEFAUT = "0".repeat(64);
    public static final Duration DELAI_ATTENTE_VERROU_PAR_DEFAUT = Duration.ofSeconds(10);
    public static final int TAILLE_PAGE_VERIFICATION_PAR_DEFAUT = 500;

    private static final Pattern HASH_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Duration DELAI_MAX = Duration.ofMinutes(5);
    private static final int TAILLE_PAGE_MAX = 10_000;

    public AuditProperties {
        if (hashGenese == null) {
            hashGenese = HASH_GENESE_PAR_DEFAUT;
        }
        if (!HASH_HEX.matcher(hashGenese).matches()) {
            throw new IllegalArgumentException(
                    "sentinelle.audit.hash-genese : 64 caractères hexadécimaux minuscules attendus");
        }
        if (delaiAttenteVerrou == null) {
            delaiAttenteVerrou = DELAI_ATTENTE_VERROU_PAR_DEFAUT;
        }
        if (delaiAttenteVerrou.isNegative() || delaiAttenteVerrou.isZero()
                || delaiAttenteVerrou.compareTo(DELAI_MAX) > 0) {
            throw new IllegalArgumentException(
                    "sentinelle.audit.delai-attente-verrou : durée strictement positive, au plus 5 minutes");
        }
        if (taillePageVerification == null) {
            taillePageVerification = TAILLE_PAGE_VERIFICATION_PAR_DEFAUT;
        }
        if (taillePageVerification < 1 || taillePageVerification > TAILLE_PAGE_MAX) {
            throw new IllegalArgumentException(
                    "sentinelle.audit.taille-page-verification : entre 1 et " + TAILLE_PAGE_MAX);
        }
    }
}
