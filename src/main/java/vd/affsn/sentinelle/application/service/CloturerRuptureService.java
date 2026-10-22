package vd.affsn.sentinelle.application.service;


import vd.affsn.sentinelle.application.port.in.CloturerRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.HorlogePort;
import vd.affsn.sentinelle.application.port.out.RuptureRepository;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Rupture;

public class CloturerRuptureService implements CloturerRuptureUseCase {

    private final RuptureRepository ruptures;
    private final AuditPort audit;
    private final HorlogePort horloge;

    public CloturerRuptureService(RuptureRepository ruptures, AuditPort audit, HorlogePort horloge) {
        this.ruptures = ruptures;
        this.audit = audit;
        this.horloge = horloge;
    }

    @Override
    public RuptureVue cloturer(Acteur acteur, Commande commande) {
        Rupture actuelle = ruptures.findById(commande.ruptureId())
                .orElseThrow(() -> new RuptureIntrouvableException(commande.ruptureId()));
        // Le domaine contrôle d'abord l'accès, l'état et fin >= début (agrégat immuable : pas d'effet de bord).
        Rupture cloturee = actuelle.cloturer(acteur, commande.dateFinEffective());
        if (commande.dateFinEffective().isAfter(horloge.aujourdhui())) {
            throw new ValidationMetierException("La date de fin effective ne peut pas être dans le futur");
        }
        Rupture sauvegardee = ruptures.save(cloturee);
        audit.enregistrer(new EvenementAudit(TypeEvenementAudit.CLOTURE, sauvegardee.id(), acteur,
                horloge.maintenant(), "produit " + sauvegardee.produit() + ", clôture"));
        return RuptureVue.depuis(sauvegardee);
    }
}
