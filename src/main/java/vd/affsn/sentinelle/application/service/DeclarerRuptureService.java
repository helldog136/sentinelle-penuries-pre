package vd.affsn.sentinelle.application.service;


import vd.affsn.sentinelle.application.port.in.DeclarerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;

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
        Rupture rupture = Rupture.declarer(RuptureId.generer(), commande.produit(), acteur,
                commande.periode(), commande.motif(), commande.regions(), commande.alternatives());
        Rupture sauvegardee = ruptures.save(rupture);
        // Audit en dernier : le verrou d'audit est tenu jusqu'à la fin de la transaction.
        audit.enregistrer(new EvenementAudit(TypeEvenementAudit.DECLARATION, sauvegardee.id(), acteur,
                horloge.maintenant(),
                "produit " + sauvegardee.produit() + ", " + sauvegardee.regions().size() + " régions"));
        return RuptureVue.depuis(sauvegardee);
    }
}
