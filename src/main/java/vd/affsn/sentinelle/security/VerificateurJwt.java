package vd.affsn.sentinelle.security;

import java.util.Map;
import java.util.Objects;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Role;

/**
 * Composant de validation commun aux deux surfaces (filtre REST et intercepteur gRPC, ADR-002) :
 * vérifie un jeton brut et le traduit en {@link Acteur}.
 *
 * <p>Correspondance des claims : {@code sub} → sujet ; {@code role} ∈ {DISTRIBUTEUR, PHARMACIEN,
 * PRESCRIPTEUR} (exact, obligatoire) ; {@code distributeur_id} ({@code PRO-NNNNNN}) obligatoire
 * pour DISTRIBUTEUR, interdit sinon. Tout autre claim est ignoré pour l'identité.
 *
 * <p>Tout refus lève {@link JetonInvalideException} (message générique, sans cause). Jeton de plus
 * de {@link #TAILLE_MAX_JETON} caractères refusé avant tout décodage. Rien n'est journalisé ici.
 */
public final class VerificateurJwt {

    /** Taille maximale d'un jeton brut : 8 KiB. */
    public static final int TAILLE_MAX_JETON = 8 * 1024;

    static final String CLAIM_ROLE = "role";
    static final String CLAIM_DISTRIBUTEUR = "distributeur_id";

    private final JwtDecoder decodeur;

    public VerificateurJwt(JwtDecoder decodeur) {
        this.decodeur = Objects.requireNonNull(decodeur, "décodeur");
    }

    public Acteur verifier(String jeton) {
        if (jeton == null || jeton.isBlank() || jeton.length() > TAILLE_MAX_JETON) {
            throw new JetonInvalideException();
        }
        Jwt jwt;
        try {
            jwt = decodeur.decode(jeton);
        } catch (RuntimeException refus) {
            // Toute erreur de décodage ou de validation : même refus, raison jamais propagée.
            throw new JetonInvalideException();
        }
        return acteur(jwt);
    }

    private static Acteur acteur(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();
        Role role = role(claims.get(CLAIM_ROLE));
        Object distributeur = claims.get(CLAIM_DISTRIBUTEUR);
        try {
            if (role == Role.DISTRIBUTEUR) {
                if (!(distributeur instanceof String identifiant)) {
                    throw new JetonInvalideException();
                }
                return Acteur.distributeur(jwt.getSubject(), IdentifiantDistributeur.de(identifiant));
            }
            if (claims.containsKey(CLAIM_DISTRIBUTEUR)) {
                throw new JetonInvalideException();
            }
            return new Acteur(jwt.getSubject(), role, null);
        } catch (ValidationMetierException incoherent) {
            throw new JetonInvalideException();
        }
    }

    private static Role role(Object valeur) {
        if (valeur instanceof String texte) {
            for (Role role : Role.values()) {
                if (role.name().equals(texte)) {
                    return role;
                }
            }
        }
        throw new JetonInvalideException();
    }
}
