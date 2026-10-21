package vd.affsn.sentinelle.security;

import java.util.Optional;

import io.grpc.Context;
import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Porte l'{@link Acteur} authentifié dans le contexte gRPC courant.
 *
 * <p>Contrat partagé entre l'intercepteur de sécurité et l'adaptateur gRPC :
 * <ul>
 *   <li>la clé {@link #ACTEUR} est alimentée <strong>uniquement</strong> par l'intercepteur
 *       d'authentification JWT, à partir du jeton vérifié ;</li>
 *   <li>elle n'est <strong>jamais</strong> alimentée par un champ du message gRPC, qui reste une
 *       donnée non fiable du client ;</li>
 *   <li>elle est consommée par l'adaptateur gRPC, via {@link #acteurCourant()}.</li>
 * </ul>
 */
public final class ActeurGrpcContext {

    /** Clé du contexte gRPC portant l'acteur authentifié. */
    public static final Context.Key<Acteur> ACTEUR = Context.key("sentinelle.acteur");

    private ActeurGrpcContext() {
    }

    /**
     * Renvoie l'acteur porté par le contexte gRPC courant.
     *
     * @return l'acteur authentifié, ou {@link Optional#empty()} si le contexte n'en porte aucun
     */
    public static Optional<Acteur> acteurCourant() {
        return Optional.ofNullable(ACTEUR.get());
    }
}
