package vd.affsn.sentinelle.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.MSG;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_100001;
import static vd.affsn.sentinelle.application.service.FakesEssentiel.VMP_400004;
import static vd.affsn.sentinelle.domain.Fixtures.ACTEUR_A;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.application.service.Fakes.AuditEnregistreur;
import vd.affsn.sentinelle.application.service.Fakes.Journal;
import vd.affsn.sentinelle.application.service.Fakes.RepositoryMemoire;
import vd.affsn.sentinelle.application.service.FakesEssentiel.HorlogeReglable;
import vd.affsn.sentinelle.application.service.FakesEssentiel.ReferentielMemoire;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

/** ADR-004 § 6 / AC-46 : un refus de la règle n'écrit rien et n'ajoute rien au journal d'audit. */
class AuditRefusEssentielTest {

    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 11, 1);

    @Test
    void ac46_declaration_refusee_et_mise_a_jour_refusee_laissent_le_journal_a_N_entrees() {
        Journal journal = new Journal();
        RepositoryMemoire repo = new RepositoryMemoire(journal);
        AuditEnregistreur audit = new AuditEnregistreur(journal);
        HorlogeReglable horloge = new HorlogeReglable(AUJOURDHUI);
        ReferentielMemoire referentiel = ReferentielMemoire.parDefaut();
        DeclarerRuptureService declarer = new DeclarerRuptureService(repo, audit, horloge, referentiel, null);
        MettreAJourRuptureService mettreAJour = new MettreAJourRuptureService(repo, audit, horloge, referentiel, null);

        // Journal préexistant de N = 2 entrées.
        Rupture r = Rupture.declarer(RuptureId.generer(), VMP_100001, ACTEUR_A,
                Periode.aPartirDu(LocalDate.of(2026, 10, 1)), Motif.PRODUCTION, EnumSet.of(Region.HAUTE_PAPERASSE),
                List.of(new Alternative(VMP_400004, "Même principe actif")));
        repo.avec(r);
        audit.enregistrer(new EvenementAudit(TypeEvenementAudit.DECLARATION, r.id(), ACTEUR_A,
                horloge.maintenant(), "produit VMP-100001, 1 régions"));
        audit.enregistrer(new EvenementAudit(TypeEvenementAudit.MODIFICATION, r.id(), ACTEUR_A,
                horloge.maintenant(), "produit VMP-100001, 1 régions"));
        List<EvenementAudit> journalAvant = List.copyOf(audit.evenements);
        journal.appels.clear();

        // AC-02 : déclaration de 31 jours sans alternative.
        assertThatThrownBy(() -> declarer.declarer(ACTEUR_A, new DeclarerRuptureUseCase.Commande(VMP_100001,
                Periode.entre(AUJOURDHUI, LocalDate.of(2026, 12, 2)), Motif.PRODUCTION,
                EnumSet.of(Region.HAUTE_PAPERASSE), List.of())))
                .isInstanceOf(ValidationMetierException.class)
                .hasMessage(MSG);
        // AC-21 : retrait des alternatives d'une rupture sans fin estimée.
        assertThatThrownBy(() -> mettreAJour.mettreAJour(ACTEUR_A, new MettreAJourRuptureUseCase.Commande(r.id(),
                null, Motif.PRODUCTION, EnumSet.of(Region.HAUTE_PAPERASSE), List.of())))
                .isInstanceOf(ValidationMetierException.class)
                .hasMessage(MSG);

        assertThat(audit.evenements).containsExactlyElementsOf(journalAvant);
        assertThat(journal.appels).isEmpty();
        assertThat(repo.sauvegardes).isEmpty();
        assertThat(repo.donnees).containsOnlyKeys(r.id());
        assertThat(repo.findById(r.id()).orElseThrow()).isSameAs(r);
    }
}
