package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;

class ActeurAuthenticationTest {

    @Test
    void lePrincipalEstLActeurEtLAutoriteEstRolePrefixe() {
        Acteur pharmacien = Acteur.pharmacien(JwtTestFactory.SUJET_PHARMACIEN);
        ActeurAuthentication authentication = new ActeurAuthentication(pharmacien);

        assertThat(authentication.getPrincipal()).isSameAs(pharmacien);
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_PHARMACIEN");
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo(JwtTestFactory.SUJET_PHARMACIEN);
    }

    @Test
    void autoritesDesAutresRoles() {
        assertThat(new ActeurAuthentication(Acteur.prescripteur("p")).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_PRESCRIPTEUR");
        assertThat(new ActeurAuthentication(Acteur.distributeur("d", IdentifiantDistributeur.de("PRO-111111")))
                .getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_DISTRIBUTEUR");
    }

    @Test
    void leJetonNEstJamaisConserveCommeCredential() {
        ActeurAuthentication authentication = new ActeurAuthentication(Acteur.pharmacien("p"));
        assertThat(authentication.getCredentials()).isNull();
    }
}
