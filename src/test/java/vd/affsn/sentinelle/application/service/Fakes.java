package vd.affsn.sentinelle.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/** Doublures en mémoire partagées par les tests de services (pas de Mockito). */
final class Fakes {

    private Fakes() {
    }

    /** Journal commun aux fakes pour vérifier l'ordre des appels (« save », « audit »). */
    static final class Journal {
        final List<String> appels = new ArrayList<>();
    }

    static class RepositoryMemoire implements RuptureRepository {
        final Map<RuptureId, Rupture> donnees = new LinkedHashMap<>();
        final Journal journal;
        final List<Rupture> sauvegardes = new ArrayList<>();
        RuntimeException echecSave;

        RepositoryMemoire() {
            this(new Journal());
        }

        RepositoryMemoire(Journal journal) {
            this.journal = journal;
        }

        /** Pré-charge une rupture sans passer par save (n'apparaît pas dans le journal). */
        RepositoryMemoire avec(Rupture... ruptures) {
            for (Rupture r : ruptures) {
                donnees.put(r.id(), r);
            }
            return this;
        }

        @Override
        public Rupture save(Rupture rupture) {
            journal.appels.add("save");
            if (echecSave != null) {
                throw echecSave;
            }
            donnees.put(rupture.id(), rupture);
            sauvegardes.add(rupture);
            return rupture;
        }

        @Override
        public Optional<Rupture> findById(RuptureId id) {
            return Optional.ofNullable(donnees.get(id));
        }

        @Override
        public Page<Rupture> rechercherEnCours(Optional<Region> region, Optional<CodeProduit> produit,
                                               Pagination pagination) {
            List<Rupture> filtrees = donnees.values().stream()
                    .filter(r -> r.statut() == StatutRupture.EN_COURS)
                    .filter(r -> region.map(r.regions()::contains).orElse(true))
                    .filter(r -> produit.map(r.produit()::equals).orElse(true))
                    .sorted(Comparator.comparing((Rupture r) -> r.periode().debut()).reversed()
                            .thenComparing(r -> r.id().toString()))
                    .toList();
            int de = (int) Math.min(pagination.decalage(), filtrees.size());
            int a = Math.min(de + pagination.taille(), filtrees.size());
            return new Page<>(filtrees.subList(de, a), pagination, filtrees.size());
        }

        @Override
        public List<Rupture> findEnCoursByProduit(CodeProduit produit) {
            return donnees.values().stream()
                    .filter(r -> r.statut() == StatutRupture.EN_COURS && r.produit().equals(produit))
                    .toList();
        }

        @Override
        public List<Rupture> findActivesAu(LocalDate date) {
            return donnees.values().stream().filter(r -> r.estActiveLe(date)).toList();
        }
    }

    static final class AuditEnregistreur implements AuditPort {
        final List<EvenementAudit> evenements = new ArrayList<>();
        final Journal journal;
        RuntimeException echec;

        AuditEnregistreur() {
            this(new Journal());
        }

        AuditEnregistreur(Journal journal) {
            this.journal = journal;
        }

        @Override
        public void enregistrer(EvenementAudit evenement) {
            journal.appels.add("audit");
            if (echec != null) {
                throw echec;
            }
            evenements.add(evenement);
        }
    }

    static final class HorlogeFixe implements HorlogePort {
        final Instant instant;
        final LocalDate jour;

        HorlogeFixe(Instant instant, LocalDate jour) {
            this.instant = instant;
            this.jour = jour;
        }

        @Override
        public Instant maintenant() {
            return instant;
        }

        @Override
        public LocalDate aujourdhui() {
            return jour;
        }
    }
}
