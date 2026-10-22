package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Context;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import vd.affsn.sentinelle.domain.model.Acteur;

class ActeurGrpcContextTest {

    @Test
    void acteurCourantEstVideHorsContexte() {
        assertThat(ActeurGrpcContext.acteurCourant()).isEmpty();
    }

    @Test
    void acteurCourantPorteLActeurDuContexteEtRetombeAVideApres() throws Exception {
        Acteur pharmacien = Acteur.pharmacien("pharmacien-fictif-01");

        Optional<Acteur> dansContexte = Context.current()
                .withValue(ActeurGrpcContext.ACTEUR, pharmacien)
                .call(ActeurGrpcContext::acteurCourant);

        assertThat(dansContexte).contains(pharmacien);
        assertThat(ActeurGrpcContext.acteurCourant()).isEmpty();
    }

    @Test
    void acteurCourantRestitueLeSujetDeLActeur() throws Exception {
        Acteur prescripteur = Acteur.prescripteur("prescripteur-fictif-02");

        Optional<Acteur> dansContexte = Context.current()
                .withValue(ActeurGrpcContext.ACTEUR, prescripteur)
                .call(ActeurGrpcContext::acteurCourant);

        assertThat(dansContexte).hasValueSatisfying(
                a -> assertThat(a.sujet()).isEqualTo("prescripteur-fictif-02"));
    }
}
