package vd.affsn.sentinelle.application.service;


import vd.affsn.sentinelle.application.port.in.MettreAJourRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import java.time.LocalDate;

import vd.affsn.sentinelle.application.port.out.ReferentielProduitsPort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Rupture;

public class MettreAJourRuptureService implements MettreAJourRuptureUseCase {

    private final RuptureRepository ruptures;
    private final AuditPort audit;
    private final HorlogePort horloge;
    private final ReferentielProduitsPort referentiel;
    private final LocalDate dateEntreeEnVigueur;

    /** Compatibilité : aucun référentiel (aucun produit essentiel), règle immédiate. */
    public MettreAJourRuptureService(RuptureRepository ruptures, AuditPort audit, HorlogePort horloge) {
        this(ruptures, audit, horloge, null, null);
    }

    /** {@code dateEntreeEnVigueur} nullable = règle active immédiatement. */
    public MettreAJourRuptureService(RuptureRepository ruptures, AuditPort audit, HorlogePort horloge,
            ReferentielProduitsPort referentiel, LocalDate dateEntreeEnVigueur) {
        this.ruptures = ruptures;
        this.audit = audit;
        this.horloge = horloge;
        this.referentiel = referentiel;
        this.dateEntreeEnVigueur = dateEntreeEnVigueur;
    }

    @Override
    public RuptureVue mettreAJour(Acteur acteur, Commande commande) {
        if (referentiel != null) {
            throw new UnsupportedOperationException("à implémenter");
        }
        Rupture actuelle = ruptures.findById(commande.ruptureId())
                .orElseThrow(() -> new RuptureIntrouvableException(commande.ruptureId()));
        Periode periode = new Periode(actuelle.periode().debut(), commande.finEstimee());
        Rupture modifiee = actuelle.mettreAJour(acteur, periode, commande.motif(), commande.regions(),
                commande.alternatives());
        Rupture sauvegardee = ruptures.save(modifiee);
        audit.enregistrer(new EvenementAudit(TypeEvenementAudit.MODIFICATION, sauvegardee.id(), acteur,
                horloge.maintenant(),
                "produit " + sauvegardee.produit() + ", " + sauvegardee.regions().size() + " régions"));
        return RuptureVue.depuis(sauvegardee);
    }
}
