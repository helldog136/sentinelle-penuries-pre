package vd.affsn.sentinelle.application.service;


import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.domain.model.Acteur;

/** STUB (phase A du TDD) : aucune logique, l'implémentation viendra en phase B. */
public class DeclarerRuptureService implements DeclarerRuptureUseCase {

    private final RuptureRepository ruptures;
    private final AuditPort audit;
    private final HorlogePort horloge;

    public DeclarerRuptureService(RuptureRepository ruptures, AuditPort audit, HorlogePort horloge) {
        this.ruptures = ruptures;
        this.audit = audit;
        this.horloge = horloge;
    }

    @Override
    public RuptureVue declarer(Acteur acteur, Commande commande) {
        throw new UnsupportedOperationException("à implémenter (phase B)");
    }
}
