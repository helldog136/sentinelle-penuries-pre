package vd.affsn.sentinelle.adapter.in.grpc;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import vd.affsn.sentinelle.grpc.v1.Alternative;
import vd.affsn.sentinelle.grpc.v1.MotifRupture;
import vd.affsn.sentinelle.grpc.v1.Region;

/**
 * Validation au bord des requêtes gRPC (règles du proto). Accumule des violations
 * {@code champ: raison}. Les raisons ne reprennent jamais la valeur saisie.
 */
final class ValidateurRequetes {

    static final int REGIONS_MAX = 6;
    static final int ALTERNATIVES_MAX = 20;
    static final int COMMENTAIRE_MAX = 280;

    private static final Pattern CODE_PRODUIT = Pattern.compile("VMP-[0-9]{6}");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern UUID_CANONIQUE =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final List<String> violations = new ArrayList<>();

    boolean valide() {
        return violations.isEmpty();
    }

    /** Violations séparées par {@code "; "}. */
    String message() {
        return String.join("; ", violations);
    }

    void violation(String champ, String raison) {
        violations.add(champ + ": " + raison);
    }

    /** Analyse une date {@code AAAA-MM-JJ} stricte ; ajoute une violation et renvoie vide sinon. */
    Optional<LocalDate> date(String champ, String valeur) {
        if (DATE.matcher(valeur).matches()) {
            try {
                return Optional.of(LocalDate.parse(valeur));
            } catch (DateTimeParseException e) {
                // date calendaire inexistante : violation ci-dessous
            }
        }
        violation(champ, "format attendu AAAA-MM-JJ, date existante");
        return Optional.empty();
    }

    void codeProduit(String champ, String valeur) {
        if (!CODE_PRODUIT.matcher(valeur).matches()) {
            violation(champ, "format attendu VMP-NNNNNN");
        }
    }

    void uuid(String champ, String valeur) {
        if (!UUID_CANONIQUE.matcher(valeur).matches()) {
            violation(champ, "UUID canonique en minuscules attendu");
        }
    }

    void motif(MotifRupture motif) {
        if (motif == MotifRupture.MOTIF_RUPTURE_UNSPECIFIED || motif == MotifRupture.UNRECOGNIZED) {
            violation("motif", "valeur obligatoire");
        }
    }

    void regions(List<Region> regions) {
        if (regions.isEmpty() || regions.size() > REGIONS_MAX) {
            violation("regions", "1 à " + REGIONS_MAX + " régions attendues");
        }
        if (regions.stream().anyMatch(r -> r == Region.REGION_UNSPECIFIED || r == Region.UNRECOGNIZED)) {
            violation("regions", "valeur non spécifiée ou inconnue");
        }
        if (new HashSet<>(regions).size() != regions.size()) {
            violation("regions", "doublon interdit");
        }
    }

    /** @param produit code du produit en rupture, ou {@code null} s'il n'est pas connu (mise à jour) */
    void alternatives(List<Alternative> alternatives, String produit) {
        if (alternatives.size() > ALTERNATIVES_MAX) {
            violation("alternatives", "au plus " + ALTERNATIVES_MAX + " alternatives");
        }
        Set<String> vus = new HashSet<>();
        for (int i = 0; i < alternatives.size(); i++) {
            Alternative alternative = alternatives.get(i);
            String code = alternative.getCodeProduit();
            String champCode = "alternatives[" + i + "].code_produit";
            if (!CODE_PRODUIT.matcher(code).matches()) {
                violation(champCode, "format attendu VMP-NNNNNN");
            } else if (code.equals(produit)) {
                violation(champCode, "doit différer du produit en rupture");
            } else if (!vus.add(code)) {
                violation(champCode, "doublon interdit");
            }
            commentaire("alternatives[" + i + "].commentaire", alternative.getCommentaire());
        }
    }

    private void commentaire(String champ, String valeur) {
        if (valeur.chars().anyMatch(Character::isISOControl)) {
            violation(champ, "caractère de contrôle interdit");
        } else if (valeur.strip().length() > COMMENTAIRE_MAX) {
            violation(champ, COMMENTAIRE_MAX + " caractères au plus");
        }
    }
}
