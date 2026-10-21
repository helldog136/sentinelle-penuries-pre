package vd.affsn.sentinelle.adapter.out.audit;

import java.util.Objects;
import java.util.Optional;

/**
 * Résultat explicite d'une vérification d'intégrité du journal d'audit.
 *
 * <ul>
 *   <li>{@code integre} : vrai si toute la chaîne relue est cohérente.</li>
 *   <li>{@code entreesVerifiees} : nombre d'entrées dont le hash et le chaînage ont été validés
 *       (avant la première rupture le cas échéant).</li>
 *   <li>{@code sequencePremiereRupture} / {@code nature} : présents si et seulement si
 *       {@code integre} est faux.</li>
 *   <li>{@code derniereSequenceVerifiee} / {@code dernierHashVerifie} : dernière entrée valide
 *       relue (absents si le journal est vide ou si la tête est rompue). Le job nocturne doit
 *       publier ce couple hors de la base (ancrage externe) : c'est le seul moyen de détecter une
 *       troncature de la queue du journal, invisible par le seul chaînage.</li>
 * </ul>
 */
public record ResultatVerificationIntegrite(boolean integre,
                                            long entreesVerifiees,
                                            Long sequencePremiereRupture,
                                            NatureRupture nature,
                                            Long derniereSequenceVerifiee,
                                            String dernierHashVerifie) {

    /** Nature de la première rupture constatée. */
    public enum NatureRupture {
        /** Le hash recalculé à partir du contenu stocké diffère de {@code hash_courant} : contenu altéré. */
        HASH_RECALCULE_DIFFERENT,
        /**
         * {@code hash_precedent} ne référence ni l'entrée qui précède, ni aucune entrée antérieure :
         * entrée supprimée, insérée ou réordonnée, ou hash de genèse modifié.
         */
        CHAINAGE_ROMPU,
        /**
         * {@code hash_precedent} référence une entrée antérieure déjà chaînée (ou la genèse) : deux
         * entrées partagent le même prédécesseur, la chaîne a bifurqué (écritures concurrentes non
         * sérialisées, typiquement plusieurs instances).
         */
        FOURCHE
    }

    public ResultatVerificationIntegrite {
        if (entreesVerifiees < 0) {
            throw new IllegalArgumentException("entreesVerifiees négatif");
        }
        boolean ruptureAbsente = sequencePremiereRupture == null && nature == null;
        boolean ruptureComplete = sequencePremiereRupture != null && nature != null;
        if (integre ? !ruptureAbsente : !ruptureComplete) {
            throw new IllegalArgumentException(
                    "séquence et nature de rupture : toutes deux absentes si intègre, toutes deux présentes sinon");
        }
    }

    static ResultatVerificationIntegrite integre(long entrees, Long derniereSequence, String dernierHash) {
        return new ResultatVerificationIntegrite(true, entrees, null, null, derniereSequence, dernierHash);
    }

    static ResultatVerificationIntegrite rompu(long entrees, long sequence, NatureRupture nature,
                                               Long derniereSequence, String dernierHash) {
        return new ResultatVerificationIntegrite(false, entrees, sequence,
                Objects.requireNonNull(nature, "nature"), derniereSequence, dernierHash);
    }

    public Optional<Long> premiereRupture() {
        return Optional.ofNullable(sequencePremiereRupture);
    }

    public Optional<NatureRupture> natureRupture() {
        return Optional.ofNullable(nature);
    }
}
