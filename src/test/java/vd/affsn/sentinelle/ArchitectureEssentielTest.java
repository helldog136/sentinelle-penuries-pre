package vd.affsn.sentinelle;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.grpc.v1.DeclarerRuptureRequest;
import vd.affsn.sentinelle.grpc.v1.Rupture;

/**
 * Architecture du référentiel des produits essentiels (ADR-004, AC-66 et AC-67), en complément
 * d'{@link ArchitectureTest} (qui garde déjà domain et application sans Spring).
 *
 * <p>L'adaptateur du référentiel n'existe pas encore (TDD, phase rouge) : il est désigné par son
 * NOM QUALIFIÉ en chaîne, jamais importé, pour que cette classe compile avant lui.
 */
@AnalyzeClasses(
    packages = "vd.affsn.sentinelle",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureEssentielTest {

  private static final String ROOT = "vd.affsn.sentinelle";
  private static final String PORT = ROOT + ".application.port.out.ReferentielProduitsPort";
  private static final String ADAPTATEUR_JPA =
      ROOT + ".adapter.out.persistence.ReferentielProduitsJpaAdapter";
  private static final String CODE_PRODUIT = ROOT + ".domain.model.CodeProduit";
  private static final String REGLE = ROOT + ".domain.regle.RegleAlternativeProduitEssentiel";
  private static final String CALCULATEUR = ROOT + ".domain.tension.CalculateurTension";
  private static final String PERSISTENCE = ROOT + ".adapter.out.persistence..";
  private static final String ADAPTER = ROOT + ".adapter..";
  private static final String ADAPTER_IN = ROOT + ".adapter.in..";
  private static final String BATCH = ROOT + ".batch..";

  // ====================================================================== AC-66 : le port

  @ArchTest
  static final ArchRule port_declare_dans_application_port_out =
      classes()
          .that()
          .haveFullyQualifiedName(PORT)
          .should()
          .resideInAPackage(ROOT + ".application.port.out")
          .andShould()
          .beInterfaces()
          .andShould()
          .bePublic()
          .as("ReferentielProduitsPort est une interface publique de application.port.out")
          .because("ADR-004 § 1 : l'accès au référentiel passe par un port sortant d'application");

  @ArchTest
  static void port_n_expose_que_estEssentiel_en_lecture(JavaClasses classes) {
    JavaClass port = classes.get(PORT);
    Set<JavaMethod> methodes = port.getMethods();

    assertThat(methodes)
        .as("ADR-004 § 1 : une seule méthode, aucune opération d'écriture")
        .hasSize(1);
    JavaMethod unique = methodes.iterator().next();
    assertThat(unique.getName()).isEqualTo("estEssentiel");
    assertThat(unique.getModifiers()).contains(JavaModifier.PUBLIC).doesNotContain(JavaModifier.STATIC);
    assertThat(unique.getRawReturnType().getName()).isEqualTo("boolean");
    assertThat(unique.getRawParameterTypes())
        .extracting(JavaClass::getName)
        .containsExactly(CODE_PRODUIT);
    // Ni méthode héritée d'une super-interface (qui pourrait apporter une écriture).
    assertThat(port.getRawInterfaces()).as("aucune super-interface").isEmpty();
  }

  // ====================================================================== AC-66 : l'implémentation

  @ArchTest
  static final ArchRule implementations_du_port_dans_adapter_out_persistence =
      classes()
          .that()
          .implement(PORT)
          .should()
          .resideInAPackage(PERSISTENCE)
          // Volontairement NON tolérant au vide : il faut au moins une implémentation.
          .allowEmptyShould(false)
          .as("ReferentielProduitsPort est implémenté (au moins une fois) et uniquement dans"
              + " adapter.out.persistence")
          .because("ADR-004 § 1 et ADR-001 : implémentation du port dans adapter.out.persistence");

  @ArchTest
  static void adaptateur_jpa_du_referentiel_present_et_branche_sur_le_port(JavaClasses classes) {
    assertThat(classes.contain(ADAPTATEUR_JPA))
        .as("classe attendue : %s", ADAPTATEUR_JPA)
        .isTrue();
    JavaClass adaptateur = classes.get(ADAPTATEUR_JPA);
    assertThat(adaptateur.isAssignableTo(PORT)).as("implémente ReferentielProduitsPort").isTrue();
    assertThat(adaptateur.isInterface()).isFalse();
  }

  // ====================================================================== AC-66/67 : aucune écriture depuis les entrants

  @ArchTest
  static final ArchRule adaptateurs_entrants_sans_acces_direct_au_referentiel =
      noClasses()
          .that()
          .resideInAPackage(ADAPTER_IN)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(PORT)
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName(ADAPTATEUR_JPA)
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage(PERSISTENCE)
          .allowEmptyShould(true)
          .as("adapter.in n'accède au référentiel ni par le port ni par son adaptateur")
          .because(
              "ADR-004 § 1 : ni gRPC ni REST ne lisent ou modifient le référentiel en tant que tel ;"
                  + " le caractère essentiel arrive par les cas d'usage");

  @ArchTest
  static final ArchRule aucun_adaptateur_ne_porte_la_regle =
      noClasses()
          .that()
          .resideInAPackage(ADAPTER)
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName(REGLE)
          .allowEmptyShould(true)
          .as("aucun adaptateur ne porte la règle d'alternative obligatoire")
          .because("ADR-004 § 3 : la règle est dans le domaine, appelée par les cas d'usage");

  @ArchTest
  static final ArchRule regle_pure_dans_le_domaine =
      classes()
          .that()
          .haveFullyQualifiedName(REGLE)
          .should()
          .resideInAPackage(ROOT + ".domain..")
          .andShould()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", ROOT + ".domain..")
          .as("la règle d'alternative obligatoire est du pur Java du domaine (aucune I/O, aucun framework)")
          .because("ADR-004 § 3");

  @Test
  void aucune_ecriture_sql_sur_produits_essentiels_dans_le_code_de_production() throws IOException {
    Pattern ecriture =
        Pattern.compile(
            "(INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|MERGE\\s+INTO|TRUNCATE\\s+TABLE)\\s+\"?produits_essentiels",
            Pattern.CASE_INSENSITIVE);
    List<String> fautifs = new ArrayList<>();
    for (Path racine : List.of(Path.of("src/main/java"), Path.of("src/main/resources"))) {
      try (Stream<Path> fichiers = Files.walk(racine)) {
        for (Path f : fichiers.filter(Files::isRegularFile).toList()) {
          // Les migrations Flyway créent le schéma ; l'alimentation relève de l'AFFSN (questions ouvertes).
          if (f.toString().replace('\\', '/').contains("db/migration/")) {
            continue;
          }
          if (ecriture.matcher(Files.readString(f)).find()) {
            fautifs.add(f.toString());
          }
        }
      }
    }
    assertThat(fautifs).as("ADR-004 § 1 : le référentiel est en lecture seule pour Sentinelle").isEmpty();
  }

  // ====================================================================== AC-67 : contrats

  @Test
  void contrat_grpc_aucune_rpc_de_referentiel_et_aucune_requete_ne_porte_essentiel() {
    FileDescriptor fichier = DeclarerRuptureRequest.getDescriptor().getFile();
    List<String> rpcs = new ArrayList<>();
    for (ServiceDescriptor service : fichier.getServices()) {
      for (MethodDescriptor rpc : service.getMethods()) {
        rpcs.add(service.getName() + "/" + rpc.getName());
        assertThat(champsRecursifs(rpc.getInputType(), new HashSet<>()))
            .as("champs de la requête %s", rpc.getInputType().getName())
            .noneMatch(nom -> nom.toLowerCase().contains("essentiel"));
      }
    }
    assertThat(rpcs)
        .containsExactlyInAnyOrder(
            "SignalementService/DeclarerRupture",
            "SignalementService/MettreAJourRupture",
            "SignalementService/CloturerRupture");

    // Sortie uniquement : bool essentiel = 13 dans Rupture (11 et 12 restent réservés).
    FieldDescriptor essentiel = Rupture.getDescriptor().findFieldByName("essentiel");
    assertThat(essentiel).isNotNull();
    assertThat(essentiel.getNumber()).isEqualTo(13);
    assertThat(essentiel.getType()).isEqualTo(FieldDescriptor.Type.BOOL);
  }

  private static List<String> champsRecursifs(Descriptor message, Set<String> vus) {
    List<String> noms = new ArrayList<>();
    if (!vus.add(message.getFullName())) {
      return noms;
    }
    for (FieldDescriptor champ : message.getFields()) {
      noms.add(message.getName() + "." + champ.getName());
      if (champ.getJavaType() == FieldDescriptor.JavaType.MESSAGE) {
        noms.addAll(champsRecursifs(champ.getMessageType(), vus));
      }
    }
    return noms;
  }

  @ArchTest
  static final ArchRule surface_rest_en_lecture_seule =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".adapter.in.rest..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.bind.annotation.PostMapping")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.bind.annotation.PutMapping")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.bind.annotation.PatchMapping")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.bind.annotation.DeleteMapping")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.bind.annotation.RequestBody")
          .allowEmptyShould(true)
          .as("la surface REST reste en lecture seule (aucun corps de requête, aucune écriture)")
          .because("ADR-004 § 1 et AC-67 : pas d'API d'écriture du référentiel");

  @Test
  void contrat_openapi_sans_operation_d_ecriture_ni_corps_de_requete() throws IOException {
    String openapi = Files.readString(Path.of("docs/openapi.yaml"));
    assertThat(openapi).doesNotContain("requestBody");
    assertThat(Pattern.compile("(?m)^\\s{4}(post|put|patch|delete):").matcher(openapi).find())
        .as("aucune opération d'écriture dans docs/openapi.yaml")
        .isFalse();
  }

  // ====================================================================== § 4 : pondération câblée dans le batch

  @ArchTest
  static final ArchRule batch_utilise_le_calcul_pondere =
      classes()
          .that()
          .resideInAPackage(BATCH)
          .and()
          .haveSimpleName("CalculTensionTasklet")
          .should()
          .callMethod(CALCULATEUR, "calculer", Collection.class.getName(), LocalDate.class.getName(),
              Predicate.class.getName())
          .as("le batch calcule l'indice pondéré (statut essentiel résolu par l'application)")
          .because("ADR-004 § 4 : les produits essentiels comptent double");

  @ArchTest
  static final ArchRule batch_n_utilise_plus_le_calcul_non_pondere =
      noClasses()
          .that()
          .resideInAPackage(BATCH)
          .should()
          .callMethod(CALCULATEUR, "calculer", Collection.class.getName(), LocalDate.class.getName())
          .allowEmptyShould(true)
          .as("le batch n'appelle plus la variante non pondérée du calcul")
          .because("ADR-004 § 4 : la pondération ne dépend d'aucune date et s'applique à tous les calculs");
}
