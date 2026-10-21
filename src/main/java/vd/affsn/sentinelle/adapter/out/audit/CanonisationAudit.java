package vd.affsn.sentinelle.adapter.out.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import vd.affsn.sentinelle.application.port.out.EvenementAudit;

/**
 * Sérialisation canonique et hachage SHA-256 d'une entrée du journal d'audit (ADR-003).
 *
 * <h2>Format canonique, version 1 (contrat de vérification : ne pas modifier sans migrer)</h2>
 *
 * Suite d'octets UTF-8 :
 *
 * <pre>
 * "sentinelle-audit-v1"
 * puis, pour chaque champ, dans cet ordre exact :
 *   "\n" + nom + "=" + L + ":" + valeur
 * </pre>
 *
 * où {@code L} est le nombre d'<b>octets UTF-8</b> de {@code valeur}, écrit en décimal sans zéro
 * de tête. Le préfixe de longueur rend la sérialisation sans ambiguïté quel que soit le contenu
 * des valeurs (y compris « \n », « = », « : » ou chiffres). Champs, dans l'ordre :
 *
 * <ol>
 *   <li>{@code evenement_id} : UUID, forme canonique minuscule ({@link java.util.UUID#toString()}) ;</li>
 *   <li>{@code type} : nom de l'énumération ({@code DECLARATION}, {@code MODIFICATION}, {@code CLOTURE}) ;</li>
 *   <li>{@code rupture_id} : UUID, forme canonique minuscule ;</li>
 *   <li>{@code sujet} : sujet de l'acteur, tel que stocké ;</li>
 *   <li>{@code role} : nom du rôle ;</li>
 *   <li>{@code instant} : UTC, {@code uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'} (toujours 6 décimales,
 *       instant préalablement tronqué à la microseconde) ;</li>
 *   <li>{@code resume} : résumé assaini, tel que stocké ;</li>
 *   <li>{@code hash_precedent} : 64 caractères hexadécimaux minuscules.</li>
 * </ol>
 *
 * {@code hash_courant} = SHA-256 de cette suite d'octets, en hexadécimal minuscule (64 caractères).
 * Un tiers peut recalculer la chaîne à partir de la seule table et de ce commentaire.
 */
final class CanonisationAudit {

    static final String VERSION = "sentinelle-audit-v1";

    private static final DateTimeFormatter FORMAT_INSTANT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ROOT)
                    .withZone(ZoneOffset.UTC);

    private static final HexFormat HEX = HexFormat.of(); // minuscules par défaut

    /** Jeton JWT compact (en-tête base64url commençant par « {" ») ou en-tête Bearer. */
    private static final Pattern JETON_PROBABLE = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*|(?i:bearer)\\s+\\S+");

    static final String MASQUE = "[masqué]";

    private CanonisationAudit() {
    }

    /** Tronque à la microseconde : précision commune à H2 et PostgreSQL (timestamptz). */
    static Instant tronquer(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    static String formaterInstant(Instant instantTronque) {
        return FORMAT_INSTANT.format(instantTronque);
    }

    /**
     * Assainissement défensif du résumé, indépendamment de la validation du port : masque ce qui
     * ressemble à un jeton, remplace tout caractère de contrôle, de format (ex. surcharges
     * bidirectionnelles) ou séparateur de ligne/paragraphe Unicode par une espace, retire les
     * espaces de bord, puis tronque à {@link EvenementAudit#LONGUEUR_MAX_RESUME} unités UTF-16
     * sans couper une paire de substitution (donc au plus 500 caractères côté base).
     *
     * <p>Limite assumée : aucune heuristique ne détecte de façon fiable une donnée patient ; la
     * responsabilité première reste aux appelants du port.
     */
    static String assainirResume(String resume) {
        Objects.requireNonNull(resume, "resume");
        String masque = JETON_PROBABLE.matcher(resume).replaceAll(MASQUE);
        StringBuilder sb = new StringBuilder(Math.min(masque.length(), EvenementAudit.LONGUEUR_MAX_RESUME + 16));
        masque.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp)
                    || Character.getType(cp) == Character.FORMAT
                    || Character.getType(cp) == Character.LINE_SEPARATOR
                    || Character.getType(cp) == Character.PARAGRAPH_SEPARATOR
                    // substitut isolé : non encodable en UTF-8, l'octet stocké différerait du haché
                    || Character.getType(cp) == Character.SURROGATE) {
                sb.append(' ');
            } else {
                sb.appendCodePoint(cp);
            }
        });
        String propre = sb.toString().strip();
        if (propre.length() > EvenementAudit.LONGUEUR_MAX_RESUME) {
            int fin = EvenementAudit.LONGUEUR_MAX_RESUME;
            if (Character.isHighSurrogate(propre.charAt(fin - 1))) {
                fin--;
            }
            propre = propre.substring(0, fin).strip();
        }
        return propre;
    }

    /** Calcule le hash courant à partir des valeurs exactement telles que stockées en base. */
    static String hacher(String evenementId,
                         String type,
                         String ruptureId,
                         String sujet,
                         String role,
                         String instantCanonique,
                         String resume,
                         String hashPrecedent) {
        MessageDigest sha256 = nouveauSha256();
        sha256.update(VERSION.getBytes(StandardCharsets.UTF_8));
        champ(sha256, "evenement_id", evenementId);
        champ(sha256, "type", type);
        champ(sha256, "rupture_id", ruptureId);
        champ(sha256, "sujet", sujet);
        champ(sha256, "role", role);
        champ(sha256, "instant", instantCanonique);
        champ(sha256, "resume", resume);
        champ(sha256, "hash_precedent", hashPrecedent);
        return HEX.formatHex(sha256.digest());
    }

    private static void champ(MessageDigest md, String nom, String valeur) {
        Objects.requireNonNull(valeur, nom);
        byte[] octets = valeur.getBytes(StandardCharsets.UTF_8);
        md.update(("\n" + nom + "=" + octets.length + ":").getBytes(StandardCharsets.UTF_8));
        md.update(octets);
    }

    private static MessageDigest nouveauSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est obligatoire sur toute JVM conforme : ne jamais continuer sans.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
