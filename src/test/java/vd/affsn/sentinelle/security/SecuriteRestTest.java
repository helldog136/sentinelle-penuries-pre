package vd.affsn.sentinelle.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import net.devh.boot.grpc.server.interceptor.GlobalServerInterceptorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import vd.affsn.sentinelle.domain.model.Acteur;

/**
 * Chaîne de sécurité REST sur le contexte complet (profil test), avec un contrôleur de SONDE
 * défini côté tests : il renvoie l'Acteur vu par Spring Security, ce qui permet de vérifier que
 * l'identité vient du jeton et jamais de la requête.
 *
 * <p>Le serveur gRPC réseau est désactivé ({@code grpc.server.port=-1}) pour ne pas entrer en
 * conflit de port avec d'autres contextes de test en cache.
 */
@SpringBootTest(properties = "grpc.server.port=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecuriteRestTest {

    private static final String SONDE = "/api/v1/sonde";
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApplicationContext contexte;

    private final JwtTestFactory jetons = JwtTestFactory.systeme();

    @TestConfiguration
    static class ConfigurationSonde {
        @Bean
        SondeController sondeController() {
            return new SondeController();
        }
    }

    /** Contrôleur de sondage : renvoie l'Acteur authentifié, ignore volontairement les champs de requête. */
    @RestController
    static class SondeController {

        @GetMapping(SONDE)
        Map<String, Object> lire(@AuthenticationPrincipal Acteur acteur,
                                 @RequestParam(name = "distributeurId", required = false) String distributeurId,
                                 @RequestHeader(name = "X-Distributeur-Id", required = false) String enteteDistributeur) {
            return decrire(acteur);
        }

        @PostMapping(SONDE)
        Map<String, Object> ecrire(@AuthenticationPrincipal Acteur acteur) {
            return decrire(acteur);
        }

        @GetMapping("/interne/sonde")
        String horsApi() {
            return "ne doit jamais répondre";
        }

        private static Map<String, Object> decrire(Acteur acteur) {
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("sujet", acteur.sujet());
            vue.put("role", acteur.role().name());
            vue.put("distributeur", acteur.identifiantDistributeur().map(Object::toString).orElse(""));
            vue.put("autorites", SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                    .stream().map(GrantedAuthority::getAuthority).toList());
            vue.put("principalEstActeur",
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof Acteur);
            return vue;
        }
    }

    private static String bearer(String jeton) {
        return "Bearer " + jeton;
    }

    // ------------------------------------------------------------------ 401

    @Test
    void sansJeton401AvecWwwAuthenticateBearerEtProblemJson() throws Exception {
        MvcResult resultat = mvc.perform(get(SONDE))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.type").isNotEmpty())
                .andReturn();

        assertThat(resultat.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    static Stream<Arguments> entetesRefuses() {
        return Stream.of(
                cas("jeton expiré", j -> bearer(j.expire())),
                cas("signature d'une autre clé", j -> bearer(j.signeParUneAutreCle())),
                cas("charge utile altérée", j -> bearer(j.signatureAlteree())),
                cas("alg none", j -> bearer(j.algNone())),
                cas("confusion HS256", j -> bearer(j.confusionHs256())),
                cas("mauvais émetteur", j -> bearer(j.mauvaisEmetteur())),
                cas("mauvaise audience", j -> bearer(j.mauvaiseAudience())),
                cas("rôle inconnu", j -> bearer(j.roleInconnu())),
                cas("DISTRIBUTEUR sans distributeur_id", j -> bearer(j.distributeurSansIdentifiant())),
                cas("PHARMACIEN avec distributeur_id", j -> bearer(j.pharmacienAvecIdentifiantDistributeur())),
                cas("jeton surdimensionné", j -> bearer(j.surdimensionne())),
                cas("jeton mal formé", j -> bearer(JwtTestFactory.malForme())),
                cas("schéma Basic", j -> "Basic dXRpbGlzYXRldXI6bW90LWRlLXBhc3Nl"),
                cas("Bearer sans jeton", j -> "Bearer "),
                cas("jeton sans schéma", j -> j.pharmacien()));
    }

    private static Arguments cas(String nom, Function<JwtTestFactory, String> entete) {
        return Arguments.of(nom, entete);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entetesRefuses")
    void enTeteRefuse401SansEchoDuJeton(String nom, Function<JwtTestFactory, String> fabrique) throws Exception {
        String entete = fabrique.apply(jetons);

        MvcResult resultat = mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, entete))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andReturn();

        String corps = resultat.getResponse().getContentAsString();
        String jetonSeul = entete.startsWith("Bearer ") ? entete.substring(7) : entete;
        if (!jetonSeul.isBlank()) {
            assertThat(corps).doesNotContain(jetonSeul);
            for (String nomEntete : resultat.getResponse().getHeaderNames()) {
                assertThat(resultat.getResponse().getHeaders(nomEntete)).noneMatch(v -> v.contains(jetonSeul));
            }
        }
    }

    @Test
    void leCorps401NeRevelePasLaRaisonDuRefus() throws Exception {
        String expire = mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jetons.expire())))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String signature = mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jetons.signeParUneAutreCle())))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String absent = mvc.perform(get(SONDE))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(expire).isEqualTo(signature).isEqualTo(absent);
        assertThat(expire.toLowerCase()).doesNotContain("expir", "signature", "exception", "nimbus", "jwt");
    }

    // ------------------------------------------------------------------ accès autorisé

    @Test
    void pharmacienAccedeAApiEtLePrincipalEstLActeur() throws Exception {
        mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jetons.pharmacien())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sujet").value(JwtTestFactory.SUJET_PHARMACIEN))
                .andExpect(jsonPath("$.role").value("PHARMACIEN"))
                .andExpect(jsonPath("$.distributeur").value(""))
                .andExpect(jsonPath("$.autorites").value(org.hamcrest.Matchers.contains("ROLE_PHARMACIEN")))
                .andExpect(jsonPath("$.principalEstActeur").value(true));
    }

    @Test
    void prescripteurAccedeAApi() throws Exception {
        mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jetons.prescripteur())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("PRESCRIPTEUR"))
                .andExpect(jsonPath("$.autorites").value(org.hamcrest.Matchers.contains("ROLE_PRESCRIPTEUR")));
    }

    @Test
    void schemaBearerInsensibleALaCasse() throws Exception {
        mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, "bearer " + jetons.pharmacien()))
                .andExpect(status().isOk());
        mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, "BEARER " + jetons.prescripteur()))
                .andExpect(status().isOk());
    }

    @Test
    void csrfDesactivePourLApiBearer() throws Exception {
        mvc.perform(post(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jetons.pharmacien())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 403

    @Test
    void distributeurRecoit403EnProblemJsonSurApi() throws Exception {
        String jeton = jetons.distributeur111();

        String corps = mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jeton)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(corps).doesNotContain(jeton).doesNotContain("PRO-111111").doesNotContainIgnoringCase("exception");
    }

    @Test
    void cheminHorsApiRefuseMemeAvecUnJetonValide() throws Exception {
        mvc.perform(get("/interne/sonde").header(HttpHeaders.AUTHORIZATION, bearer(jetons.pharmacien())))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON));
    }

    @Test
    void cheminHorsApiSansJeton401() throws Exception {
        mvc.perform(get("/interne/sonde")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ actuator

    @Test
    void healthPermisSansJeton() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void autresEndpointsActuatorRefuses() throws Exception {
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info").header(HttpHeaders.AUTHORIZATION, bearer(jetons.pharmacien())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ usurpation et état

    @Test
    void lIdentiteNePeutPasEtreUsurpeeParUnChampDeRequete() throws Exception {
        mvc.perform(get(SONDE)
                        .param("distributeurId", "PRO-111111")
                        .header("X-Distributeur-Id", "PRO-111111")
                        .header("X-Role", "DISTRIBUTEUR")
                        .header(HttpHeaders.AUTHORIZATION, bearer(jetons.pharmacien())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sujet").value(JwtTestFactory.SUJET_PHARMACIEN))
                .andExpect(jsonPath("$.role").value("PHARMACIEN"))
                .andExpect(jsonPath("$.distributeur").value(""));
    }

    @Test
    void sansSessionAucunCookieNiReutilisation() throws Exception {
        MvcResult avecJeton = mvc.perform(get(SONDE).header(HttpHeaders.AUTHORIZATION, bearer(jetons.pharmacien())))
                .andExpect(status().isOk()).andReturn();

        assertThat(avecJeton.getRequest().getSession(false)).isNull();
        assertThat(avecJeton.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        mvc.perform(get(SONDE)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ câblage

    @Test
    void cablageIntercepteurGrpcGlobalEtPasDUtilisateurParDefaut() {
        assertThat(contexte.getBean(GlobalServerInterceptorRegistry.class).getServerInterceptors())
                .anyMatch(IntercepteurAuthentificationGrpc.class::isInstance);
        assertThat(contexte.getBeansOfType(UserDetailsService.class)).isEmpty();
        assertThat(contexte.getBeansOfType(FiltreAuthentificationJwt.class))
                .as("le filtre JWT ne doit pas être un bean (double enregistrement servlet)").isEmpty();
        @SuppressWarnings("rawtypes")
        List<FilterRegistrationBean> enregistrements = List.copyOf(contexte.getBeansOfType(FilterRegistrationBean.class).values());
        assertThat(enregistrements).noneMatch(e -> e.getFilter() instanceof FiltreAuthentificationJwt);
    }
}
