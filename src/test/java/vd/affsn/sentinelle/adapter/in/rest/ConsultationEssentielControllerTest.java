package vd.affsn.sentinelle.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.CodeProduit;
import vd.affsn.sentinelle.domain.model.IdentifiantDistributeur;
import vd.affsn.sentinelle.domain.model.Motif;
import vd.affsn.sentinelle.domain.model.Periode;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.model.RuptureId;
import vd.affsn.sentinelle.domain.model.StatutRupture;

/** ADR-004 AC-37 à AC-43 : filtre REST {@code essentiel} et champ {@code essentiel} des ruptures. */
@WebMvcTest(controllers = ConsultationController.class, properties = "sentinelle.rest.enabled=true")
class ConsultationEssentielControllerTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final Acteur PHARMACIEN = Acteur.pharmacien("sujet-pharma-1");
    private static final UUID ID_A = UUID.fromString("3f8e2b1a-7c4d-4e9a-b2f0-5d6c7a8b9e01");
    private static final UUID ID_B = UUID.fromString("3f8e2b1a-7c4d-4e9a-b2f0-5d6c7a8b9e02");
    private static final UUID ID_C = UUID.fromString("3f8e2b1a-7c4d-4e9a-b2f0-5d6c7a8b9e03");

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ConsulterRupturesUseCase consulterRuptures;

    @MockBean
    private ConsulterRuptureUseCase consulterRupture;

    @MockBean
    private ConsulterAlternativesUseCase consulterAlternatives;

    private ResultActions appeler(String uri) throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                PHARMACIEN, null, List.of(new SimpleGrantedAuthority("ROLE_" + PHARMACIEN.role().name())));
        return mvc.perform(get(uri).with(authentication(auth)));
    }

    private static RuptureVue vue(UUID id, String produit, Region region, StatutRupture statut,
                                  boolean essentiel) {
        boolean cloturee = statut == StatutRupture.CLOTUREE;
        return new RuptureVue(
                new RuptureId(id),
                new CodeProduit(produit),
                new IdentifiantDistributeur("PRO-000042"),
                Periode.aPartirDu(LocalDate.of(2026, 10, 2)),
                cloturee ? Optional.of(LocalDate.of(2026, 10, 20)) : Optional.empty(),
                Motif.PRODUCTION,
                EnumSet.of(region),
                List.of(),
                statut,
                essentiel);
    }

    private ConsulterRupturesUseCase.Critere critereTransmis() {
        ArgumentCaptor<ConsulterRupturesUseCase.Critere> captor =
                ArgumentCaptor.forClass(ConsulterRupturesUseCase.Critere.class);
        verify(consulterRuptures).consulter(any(), captor.capture());
        return captor.getValue();
    }

    private void listeVide() {
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(), Pagination.premiere(), 0));
    }

    // ------------------------------------------------------------ AC-37 / AC-38 / AC-40 : filtre transmis

    @Test
    void ac37_essentielTrue_estTransmisAuCasDUsage() throws Exception {
        listeVide();

        appeler("/api/v1/ruptures?essentiel=true").andExpect(status().isOk());

        assertThat(critereTransmis().essentiel()).contains(true);
    }

    @Test
    void ac38_essentielFalse_estTransmisAuCasDUsage() throws Exception {
        listeVide();

        appeler("/api/v1/ruptures?essentiel=false").andExpect(status().isOk());

        assertThat(critereTransmis().essentiel()).contains(false);
    }

    @Test
    void ac37_laReponseFiltreeReprendTotalElementsEtPagesDuResultatFiltre() throws Exception {
        Pagination pagination = new Pagination(0, 20);
        when(consulterRuptures.consulter(any(), any())).thenReturn(new Page<>(List.of(
                vue(ID_A, "VMP-100001", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, true),
                vue(ID_C, "VMP-300003", Region.COTE_DU_TIMEOUT, StatutRupture.EN_COURS, true)), pagination, 2));

        appeler("/api/v1/ruptures?essentiel=true")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.contenu", hasSize(2)))
                .andExpect(jsonPath("$.contenu[0].id").value(ID_A.toString()))
                .andExpect(jsonPath("$.contenu[1].id").value(ID_C.toString()))
                .andExpect(jsonPath("$.contenu[0].essentiel").value(true))
                .andExpect(jsonPath("$.contenu[1].essentiel").value(true));
    }

    @Test
    void ac38_laReponseFiltreeFalseNePorteQueLesNonEssentiels() throws Exception {
        when(consulterRuptures.consulter(any(), any())).thenReturn(new Page<>(List.of(
                vue(ID_B, "VMP-200002", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, false)),
                new Pagination(0, 20), 1));

        appeler("/api/v1/ruptures?essentiel=false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].id").value(ID_B.toString()))
                .andExpect(jsonPath("$.contenu[0].essentiel").value(false));
    }

    @Test
    void ac40_essentielEtRegion_secombinent() throws Exception {
        listeVide();

        appeler("/api/v1/ruptures?essentiel=true&region=HAUTE_PAPERASSE").andExpect(status().isOk());

        ConsulterRupturesUseCase.Critere critere = critereTransmis();
        assertThat(critere.essentiel()).contains(true);
        assertThat(critere.region()).contains(Region.HAUTE_PAPERASSE);
        assertThat(critere.produit()).isEmpty();
    }

    @Test
    void essentielEtProduitEtPagination_secombinent() throws Exception {
        when(consulterRuptures.consulter(any(), any()))
                .thenReturn(new Page<>(List.of(), new Pagination(1, 50), 0));

        appeler("/api/v1/ruptures?essentiel=false&produit=VMP-200002&page=1&size=50")
                .andExpect(status().isOk());

        ConsulterRupturesUseCase.Critere critere = critereTransmis();
        assertThat(critere.essentiel()).contains(false);
        assertThat(critere.produit()).contains(new CodeProduit("VMP-200002"));
        assertThat(critere.pagination()).isEqualTo(new Pagination(1, 50));
    }

    @Test
    void sansParametreEssentiel_aucunFiltreSurLeCaractereEssentiel() throws Exception {
        listeVide();

        appeler("/api/v1/ruptures").andExpect(status().isOk());

        assertThat(critereTransmis().essentiel()).isEmpty();
    }

    // ------------------------------------------------------------ AC-39 : champ essentiel partout

    @Test
    void ac39_chaqueRuptureDeLaListePorteSonEssentiel() throws Exception {
        when(consulterRuptures.consulter(any(), any())).thenReturn(new Page<>(List.of(
                vue(ID_A, "VMP-100001", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, true),
                vue(ID_B, "VMP-200002", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, false),
                vue(ID_C, "VMP-300003", Region.COTE_DU_TIMEOUT, StatutRupture.EN_COURS, true)),
                new Pagination(0, 20), 3));

        appeler("/api/v1/ruptures")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(3)))
                .andExpect(jsonPath("$.contenu[0].essentiel").value(true))
                .andExpect(jsonPath("$.contenu[1].essentiel").value(false))
                .andExpect(jsonPath("$.contenu[2].essentiel").value(true));
    }

    @Test
    void ac39_essentielFalseEstPresentExplicitement_pasOmis() throws Exception {
        when(consulterRupture.consulter(any(), any())).thenReturn(
                vue(ID_B, "VMP-200002", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, false));

        appeler("/api/v1/ruptures/" + ID_B)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.essentiel").exists())
                .andExpect(jsonPath("$.essentiel").value(false));
    }

    // ------------------------------------------------------------ AC-41 : valeurs invalides

    @ParameterizedTest(name = "essentiel={0}")
    @ValueSource(strings = {"oui", "TRUE", "True", "FALSE", "1", "0", "vrai", "yes", "truee"})
    void ac41_valeurInvalide_renvoie400ProblemJson_violationSurEssentiel(String valeur) throws Exception {
        String corps = appeler("/api/v1/ruptures?essentiel=" + valeur)
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.violations", hasSize(1)))
                .andExpect(jsonPath("$.violations[0].champ").value("essentiel"))
                .andExpect(jsonPath("$.violations[0].message").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(texteHorsInstance(corps)).doesNotContain("=" + valeur);
        verifyNoInteractions(consulterRuptures);
    }

    @Test
    void ac41_essentielVide_estRefuse() throws Exception {
        appeler("/api/v1/ruptures?essentiel=")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.violations[0].champ").value("essentiel"));

        verifyNoInteractions(consulterRuptures);
    }

    @Test
    void ac41_essentielInvalideCumuleAvecLesAutresViolations() throws Exception {
        appeler("/api/v1/ruptures?essentiel=oui&region=ATLANTIDE")
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", PROBLEM_JSON))
                .andExpect(jsonPath("$.violations", hasSize(2)))
                .andExpect(jsonPath("$.violations[*].champ",
                        org.hamcrest.Matchers.containsInAnyOrder("essentiel", "region")));

        verifyNoInteractions(consulterRuptures);
    }

    // ------------------------------------------------------------ AC-42 / AC-43 : détail

    @Test
    void ac42_ruptureClotureeDUnProduitEssentielExposeEssentielTrue() throws Exception {
        when(consulterRupture.consulter(any(), any())).thenReturn(
                vue(ID_A, "VMP-100001", Region.HAUTE_PAPERASSE, StatutRupture.CLOTUREE, true));

        appeler("/api/v1/ruptures/" + ID_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("CLOTUREE"))
                .andExpect(jsonPath("$.dateFinEffective").value("2026-10-20"))
                .andExpect(jsonPath("$.essentiel").value(true));
    }

    @Test
    void ac43_leDetailRefleteLEtatCourantDuReferentiel_sansModificationDeLaRupture() throws Exception {
        when(consulterRupture.consulter(any(), any()))
                .thenReturn(vue(ID_A, "VMP-100001", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, true))
                .thenReturn(vue(ID_A, "VMP-100001", Region.HAUTE_PAPERASSE, StatutRupture.EN_COURS, false));

        appeler("/api/v1/ruptures/" + ID_A).andExpect(jsonPath("$.essentiel").value(true));
        appeler("/api/v1/ruptures/" + ID_A)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codeProduit").value("VMP-100001"))
                .andExpect(jsonPath("$.essentiel").value(false));
    }

    /** Corps de l'erreur sans le champ {@code instance} (URI appelée, qui contient légitimement la requête). */
    private static String texteHorsInstance(String corps) {
        Object detail = com.jayway.jsonpath.JsonPath.read(corps, "$.detail");
        Object messages = com.jayway.jsonpath.JsonPath.read(corps, "$.violations[*].message");
        return detail + " " + messages;
    }
}
