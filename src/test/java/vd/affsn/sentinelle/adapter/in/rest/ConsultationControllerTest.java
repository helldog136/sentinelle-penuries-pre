package vd.affsn.sentinelle.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import vd.affsn.sentinelle.application.port.Page;
import vd.affsn.sentinelle.application.port.Pagination;
import vd.affsn.sentinelle.application.port.in.ConsulterAlternativesUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRuptureUseCase;
import vd.affsn.sentinelle.application.port.in.ConsulterRupturesUseCase;
import vd.affsn.sentinelle.application.port.in.RuptureVue;
import vd.affsn.sentinelle.domain.exception.AccesRefuseException;
import vd.affsn.sentinelle.domain.exception.RuptureIntrouvableException;
import vd.affsn.sentinelle.domain.exception.ValidationMetierException;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.Alternative;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

@WebMvcTest(controllers = ConsultationController.class, properties = "sentinelle.rest.enabled=true")
class ConsultationControllerTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String JSON = "application/json";
    private static final UUID UUID_RUPTURE = UUID.fromString("3f8e2b1a-7c4d-4e9a-b2f0-5d6c7a8b9e01");
    private static final Acteur PHARMACIEN = Acteur.pharmacien("sujet-pharma-1");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ConsulterRupturesUseCase consulterRuptures;

    @MockBean
    private ConsulterRuptureUseCase consulterRupture;

    @MockBean
    private ConsulterAlternativesUseCase consulterAlternatives;

    private static Authentication auth(Acteur acteur) {
        return new UsernamePasswordAuthenticationToken(
                acteur, null, List.of(new SimpleGrantedAuthority("ROLE_" + acteur.role().name())));
    }

    private ResultActions appeler(String uri) throws Exception {
        return mvc.perform(get(uri).with(authentication(auth(PHARMACIEN))));
    }

    private static RuptureVue vueEnCours() {
        return new RuptureVue(
                new RuptureId(UUID_RUPTURE),
                new CodeProduit("VMP-404404"),
                new IdentifiantDistributeur("PRO-000042"),
                Periode.entre(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 12, 15)),
                Optional.empty(),
                Motif.PRODUCTION,
                EnumSet.of(Region.HAUTE_PAPERASSE, Region.COTE_DU_TIMEOUT),
                List.of(new Alternative(new CodeProduit("VMP-200200"), "Une prise par jour"),
                        Alternative.sansCommentaire(new CodeProduit("VMP-302302"))),
                StatutRupture.EN_COURS);
    }

    private static RuptureVue vueCloturee() {
        return new RuptureVue(
                new RuptureId(UUID.fromString("9a1c0d2e-3b4f-4a5b-8c6d-7e8f9a0b1c2d")),
                new CodeProduit("VMP-123456"),
                new IdentifiantDistributeur("PRO-000007"),
                Periode.aPartirDu(LocalDate.of(2026, 8, 1)),
                Optional.of(LocalDate.of(2026, 9, 12)),
                Motif.DEMANDE,
                EnumSet.of(Region.MARAIS_DE_LA_RECETTE),
                List.of(),
                StatutRupture.CLOTUREE);
    }

    // ---------------------------------------------------------------- GET /ruptures

    @Test
    void liste_renvoie200_avecCorpsConformeALOpenApi() throws Exception {
        Pagination pagination = new Pagination(0, 20);
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(vueEnCours()), pagination, 1));

        appeler("/api/v1/ruptures")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", JSON))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].id").value("3f8e2b1a-7c4d-4e9a-b2f0-5d6c7a8b9e01"))
                .andExpect(jsonPath("$.contenu[0].codeProduit").value("VMP-404404"))
                .andExpect(jsonPath("$.contenu[0].dateDebut").value("2026-10-02"))
                .andExpect(jsonPath("$.contenu[0].dateFinEstimee").value("2026-12-15"))
                .andExpect(jsonPath("$.contenu[0].dateFinEffective").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.contenu[0].motif").value("PRODUCTION"))
                .andExpect(jsonPath("$.contenu[0].regions", hasSize(2)))
                .andExpect(jsonPath("$.contenu[0].regions",
                        org.hamcrest.Matchers.containsInAnyOrder("HAUTE_PAPERASSE", "COTE_DU_TIMEOUT")))
                .andExpect(jsonPath("$.contenu[0].statut").value("EN_COURS"))
                .andExpect(jsonPath("$.contenu[0].distributeurId").value("PRO-000042"))
                .andExpect(jsonPath("$.contenu[0].alternatives", hasSize(2)))
                .andExpect(jsonPath("$.contenu[0].alternatives[0].codeProduit").value("VMP-200200"))
                .andExpect(jsonPath("$.contenu[0].alternatives[0].commentaire").value("Une prise par jour"));
    }

    @Test
    void liste_transmetLActeurDuPrincipalAuCasDUsage() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(), Pagination.premiere(), 0));

        appeler("/api/v1/ruptures").andExpect(status().isOk());

        verify(consulterRuptures).consulter(eq(PHARMACIEN), any());
    }

    @Test
    void liste_pageVide_renvoie200_avecContenuVide() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(), Pagination.premiere(), 0));

        appeler("/api/v1/ruptures")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void liste_sansParametre_utilisePage0Taille20SansFiltre() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(), Pagination.premiere(), 0));

        appeler("/api/v1/ruptures").andExpect(status().isOk());

        ConsulterRupturesUseCase.Critere critere = critereTransmis();
        assertThat(critere.pagination()).isEqualTo(new Pagination(0, 20));
        assertThat(critere.region()).isEmpty();
        assertThat(critere.produit()).isEmpty();
    }

    @Test
    void liste_filtresEtPagination_sontTransmisAuCasDUsage() throws Exception {
        Pagination pagination = new Pagination(2, 50);
        when(consulterRuptures.consulter(any(), any())).thenReturn(new Page<>(List.of(), pagination, 0));

        appeler("/api/v1/ruptures?region=COTE_DU_TIMEOUT&produit=VMP-404404&page=2&size=50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(50));

        ConsulterRupturesUseCase.Critere critere = critereTransmis();
        assertThat(critere.region()).contains(Region.COTE_DU_TIMEOUT);
        assertThat(critere.produit()).contains(new CodeProduit("VMP-404404"));
        assertThat(critere.pagination()).isEqualTo(pagination);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bornesValides")
    void liste_bornesValides_sontAcceptees(String uri, int page, int taille) throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(), new Pagination(page, taille), 0));

        appeler(uri).andExpect(status().isOk());

        assertThat(critereTransmis().pagination()).isEqualTo(new Pagination(page, taille));
    }

    static Stream<Arguments> bornesValides() {
        return Stream.of(
                Arguments.of("/api/v1/ruptures?page=0", 0, 20),
                Arguments.of("/api/v1/ruptures?size=1", 0, 1),
                Arguments.of("/api/v1/ruptures?size=100", 0, 100),
                Arguments.of("/api/v1/ruptures?page=1000&size=100", 1000, 100));
    }

    @ParameterizedTest(name = "{0} -> violation sur {1}")
    @MethodSource("requetesInvalides")
    void liste_parametreInvalide_renvoie400ProblemJson_avecViolations(
            String uri, String champ, String valeurSaisie) throws Exception {
        String corps = appeler(uri)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].champ").value(champ))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(corps).doesNotContain(valeurSaisie);
        verifyNoInteractions(consulterRuptures);
    }

    static Stream<Arguments> requetesInvalides() {
        return Stream.of(
                Arguments.of("/api/v1/ruptures?region=ATLANTIDE", "region", "ATLANTIDE"),
                Arguments.of("/api/v1/ruptures?region=cote_du_timeout", "region", "cote_du_timeout"),
                Arguments.of("/api/v1/ruptures?produit=VMP-12345", "produit", "VMP-12345"),
                Arguments.of("/api/v1/ruptures?produit=vmp-123456", "produit", "vmp-123456"),
                Arguments.of("/api/v1/ruptures?produit=VMP-1234567", "produit", "VMP-1234567"),
                Arguments.of("/api/v1/ruptures?size=0", "size", "size=0"),
                Arguments.of("/api/v1/ruptures?size=101", "size", "101"),
                Arguments.of("/api/v1/ruptures?size=zzz", "size", "zzz"),
                Arguments.of("/api/v1/ruptures?page=-1", "page", "-1"),
                Arguments.of("/api/v1/ruptures?page=zzz", "page", "zzz"));
    }

    // ---------------------------------------------------------------- GET /ruptures/{id}

    @Test
    void detail_renvoie200_pourUneRuptureEnCours() throws Exception {
        when(consulterRupture.consulter(any(), any())).thenReturn(vueEnCours());

        appeler("/api/v1/ruptures/" + UUID_RUPTURE)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", JSON))
                .andExpect(jsonPath("$.id").value(UUID_RUPTURE.toString()))
                .andExpect(jsonPath("$.codeProduit").value("VMP-404404"))
                .andExpect(jsonPath("$.dateDebut").value("2026-10-02"))
                .andExpect(jsonPath("$.dateFinEstimee").value("2026-12-15"))
                .andExpect(jsonPath("$.dateFinEffective").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.motif").value("PRODUCTION"))
                .andExpect(jsonPath("$.statut").value("EN_COURS"))
                .andExpect(jsonPath("$.distributeurId").value("PRO-000042"))
                .andExpect(jsonPath("$.alternatives", hasSize(2)));

        verify(consulterRupture).consulter(PHARMACIEN, new RuptureId(UUID_RUPTURE));
    }

    @Test
    void detail_ruptureCloturee_exposeLaDateDeFinEffective_etUneFinEstimeeNulle() throws Exception {
        RuptureVue vue = vueCloturee();
        when(consulterRupture.consulter(any(), any())).thenReturn(vue);

        appeler("/api/v1/ruptures/" + vue.id())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.dateDebut").value("2026-08-01"))
                .andExpect(jsonPath("$.dateFinEstimee").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.dateFinEffective").value("2026-09-12"))
                .andExpect(jsonPath("$.motif").value("DEMANDE"))
                .andExpect(jsonPath("$.regions[0]").value("MARAIS_DE_LA_RECETTE"))
                .andExpect(jsonPath("$.alternatives", hasSize(0)));
    }

    @ParameterizedTest(name = "id = {0}")
    @MethodSource("identifiantsInvalides")
    void detail_uuidMalForme_renvoie400ProblemJson_avecViolations(String id) throws Exception {
        String corps = appeler("/api/v1/ruptures/" + id)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].champ").value("id"))
                .andReturn().getResponse().getContentAsString();

        assertThat(texteDesErreurs(corps)).doesNotContain(id);
        verifyNoInteractions(consulterRupture);
    }

    static Stream<String> identifiantsInvalides() {
        return Stream.of("pas-un-uuid", "123", "3f8e2b1a-7c4d-4e9a-b2f0-5d6c7a8b9e0");
    }

    @Test
    void detail_ruptureIntrouvable_renvoie404ProblemJson() throws Exception {
        when(consulterRupture.consulter(any(), any()))
                .thenThrow(new RuptureIntrouvableException(new RuptureId(UUID_RUPTURE)));

        appeler("/api/v1/ruptures/" + UUID_RUPTURE)
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    @Test
    void detail_accesRefuse_renvoie403ProblemJson() throws Exception {
        when(consulterRupture.consulter(any(), any()))
                .thenThrow(new AccesRefuseException("Le rôle n'a pas accès à cette ressource"));

        appeler("/api/v1/ruptures/" + UUID_RUPTURE)
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    // ---------------------------------------------------------------- GET /produits/{code}/alternatives

    @Test
    void alternatives_renvoie200_avecCorpsConformeALOpenApi() throws Exception {
        CodeProduit produit = new CodeProduit("VMP-404404");
        when(consulterAlternatives.consulter(any(), any())).thenReturn(
                new ConsulterAlternativesUseCase.Resultat(produit, List.of(
                        new ConsulterAlternativesUseCase.AlternativeDeRupture(
                                new RuptureId(UUID_RUPTURE),
                                new Alternative(new CodeProduit("VMP-200200"), "Une prise par jour")),
                        new ConsulterAlternativesUseCase.AlternativeDeRupture(
                                new RuptureId(UUID_RUPTURE),
                                new Alternative(new CodeProduit("VMP-302302"), "Boîte de 30")))));

        appeler("/api/v1/produits/VMP-404404/alternatives")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", JSON))
                .andExpect(jsonPath("$.codeProduit").value("VMP-404404"))
                .andExpect(jsonPath("$.alternatives", hasSize(2)))
                .andExpect(jsonPath("$.alternatives[0].codeProduit").value("VMP-200200"))
                .andExpect(jsonPath("$.alternatives[0].commentaire").value("Une prise par jour"))
                .andExpect(jsonPath("$.alternatives[0].ruptureId").value(UUID_RUPTURE.toString()))
                .andExpect(jsonPath("$.alternatives[1].codeProduit").value("VMP-302302"));

        verify(consulterAlternatives).consulter(PHARMACIEN, produit);
    }

    @Test
    void alternatives_aucuneAlternative_renvoie200_avecListeVide() throws Exception {
        CodeProduit produit = new CodeProduit("VMP-999999");
        when(consulterAlternatives.consulter(any(), any()))
                .thenReturn(new ConsulterAlternativesUseCase.Resultat(produit, List.of()));

        appeler("/api/v1/produits/VMP-999999/alternatives")
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", JSON))
                .andExpect(jsonPath("$.codeProduit").value("VMP-999999"))
                .andExpect(jsonPath("$.alternatives", hasSize(0)));
    }

    @ParameterizedTest(name = "code = {0}")
    @MethodSource("codesInvalides")
    void alternatives_codeMalForme_renvoie400ProblemJson_avecViolations(String code) throws Exception {
        String corps = appeler("/api/v1/produits/" + code + "/alternatives")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].champ").value("code"))
                .andReturn().getResponse().getContentAsString();

        assertThat(texteDesErreurs(corps)).doesNotContain(code);
        verifyNoInteractions(consulterAlternatives);
    }

    static Stream<String> codesInvalides() {
        return Stream.of("VMP-12345", "vmp-123456", "ABC-123456");
    }

    // ---------------------------------------------------------------- erreurs transverses

    @Test
    void validationMetier_renvoie400ProblemJson_avecViolations() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenThrow(new ValidationMetierException("Une règle métier n'est pas respectée"));

        appeler("/api/v1/ruptures")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty());
    }

    @Test
    void accesRefuse_surLaListe_renvoie403ProblemJson() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenThrow(new AccesRefuseException("Accès refusé"));

        appeler("/api/v1/ruptures")
                .andExpect(status().isForbidden())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void erreurInattendue_renvoie500Generique_sansDetailInterneNiPile() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenThrow(new IllegalStateException("jdbc:postgresql://hote-interne/secret mot-de-passe"));

        String corps = appeler("/api/v1/ruptures")
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at vd.affsn"))))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(corps).doesNotContain("mot-de-passe").doesNotContain("stackTrace");
    }

    /** detail + messages de violations (l'URI `instance` peut légitimement contenir le chemin appelé). */
    private static String texteDesErreurs(String corps) {
        Object detail = JsonPath.read(corps, "$.detail");
        Object messages = JsonPath.read(corps, "$.violations[*].message");
        return detail + " " + messages;
    }

    private ConsulterRupturesUseCase.Critere critereTransmis() {
        ArgumentCaptor<ConsulterRupturesUseCase.Critere> captor =
                ArgumentCaptor.forClass(ConsulterRupturesUseCase.Critere.class);
        verify(consulterRuptures).consulter(any(), captor.capture());
        return captor.getValue();
    }
}
