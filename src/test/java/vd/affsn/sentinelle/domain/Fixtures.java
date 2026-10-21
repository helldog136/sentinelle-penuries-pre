package vd.affsn.sentinelle.domain;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.Rupture;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/** Jeu de données de test partagé. Tout est fictif. */
public final class Fixtures {

    public static final LocalDate DATE_REF = LocalDate.of(2026, 10, 21);

    public static final CodeProduit PRODUIT = new CodeProduit("VMP-123456");
    public static final CodeProduit PRODUIT_ALT_1 = new CodeProduit("VMP-654321");
    public static final CodeProduit PRODUIT_ALT_2 = new CodeProduit("VMP-000777");

    public static final IdentifiantDistributeur DISTRIBUTEUR_A = new IdentifiantDistributeur("PRO-000001");
    public static final IdentifiantDistributeur DISTRIBUTEUR_B = new IdentifiantDistributeur("PRO-000002");

    public static final Acteur ACTEUR_A = Acteur.distributeur("sujet-distrib-a", DISTRIBUTEUR_A);
    public static final Acteur ACTEUR_B = Acteur.distributeur("sujet-distrib-b", DISTRIBUTEUR_B);
    public static final Acteur PHARMACIEN = Acteur.pharmacien("sujet-pharma-1");
    public static final Acteur PRESCRIPTEUR = Acteur.prescripteur("sujet-presc-1");

    private Fixtures() {
    }

    /** Rupture en cours déclarée par A, débutant à la date donnée, sur les régions données. */
    public static Rupture ruptureEnCours(LocalDate debut, Region premiere, Region... autres) {
        return Rupture.declarer(
                RuptureId.generer(),
                PRODUIT,
                ACTEUR_A,
                Periode.aPartirDu(debut),
                Motif.PRODUCTION,
                EnumSet.of(premiere, autres),
                List.of(new Alternative(PRODUIT_ALT_1, "Même principe, autre dosage")));
    }

    /** Rupture déjà clôturée (reconstituée), débutant et finissant aux dates données. */
    public static Rupture ruptureCloturee(LocalDate debut, LocalDate fin, Region premiere, Region... autres) {
        return Rupture.reconstituer(
                RuptureId.generer(),
                PRODUIT,
                DISTRIBUTEUR_A,
                Periode.aPartirDu(debut),
                Motif.LOGISTIQUE,
                EnumSet.of(premiere, autres),
                List.of(),
                StatutRupture.CLOTUREE,
                fin);
    }

    public static Set<Region> regions(Region premiere, Region... autres) {
        return EnumSet.of(premiere, autres);
    }
}
