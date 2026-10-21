package vd.affsn.sentinelle.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;

import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Composant de validation commun aux deux surfaces (filtre REST et intercepteur gRPC, ADR-002) :
 * vérifie un jeton brut et le traduit en {@link Acteur}.
 *
 * <p>Correspondance des claims : {@code sub} → sujet ; {@code role} ∈ {DISTRIBUTEUR, PHARMACIEN,
 * PRESCRIPTEUR} (exact, obligatoire) ; {@code distributeur_id} ({@code PRO-NNNNNN}) obligatoire
 * pour DISTRIBUTEUR, interdit sinon. Tout autre claim est ignoré pour l'identité.
 *
 * <p>Tout refus lève {@link JetonInvalideException} (message générique). Jeton de plus de
 * {@link #TAILLE_MAX_JETON} caractères refusé avant tout décodage.
 *
 * <p>STUB (phase A).
 */
public final class VerificateurJwt {

    /** Taille maximale d'un jeton brut : 8 KiB. */
    public static final int TAILLE_MAX_JETON = 8 * 1024;

    private final JwtDecoder decodeur;

    public VerificateurJwt(JwtDecoder decodeur) {
        this.decodeur = decodeur;
    }

    public Acteur verifier(String jeton) {
        throw new UnsupportedOperationException("VerificateurJwt : phase B");
    }
}
