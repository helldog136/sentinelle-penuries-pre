package vd.affsn.sentinelle;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Verrouille l'architecture hexagonale mono-module (ADR-001).
 *
 * <p>Les règles sont tolérantes aux packages vides : les adaptateurs, batch, security et config
 * n'existent pas encore, elles sont alors préventives.
 */
@AnalyzeClasses(
    packages = "vd.affsn.sentinelle",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String ROOT = "vd.affsn.sentinelle";
  private static final String DOMAIN = ROOT + ".domain..";
  private static final String APPLICATION = ROOT + ".application..";
  private static final String APPLICATION_PORT = ROOT + ".application.port..";
  private static final String ADAPTER = ROOT + ".adapter..";
  private static final String IN_GRPC = ROOT + ".adapter.in.grpc..";
  private static final String IN_REST = ROOT + ".adapter.in.rest..";
  private static final String ADAPTER_IN = ROOT + ".adapter.in..";
  private static final String ADAPTER_OUT = ROOT + ".adapter.out..";
  private static final String BATCH = ROOT + ".batch..";
  private static final String SECURITY = ROOT + ".security..";
  private static final String CONFIG = ROOT + ".config..";

  private static final String[] FRAMEWORKS = {
    "org.springframework..", "jakarta..", "io.grpc..", "com.google.protobuf..", "com.fasterxml.."
  };

  // (1) Aucun framework dans domain et application

  @ArchTest
  static final ArchRule domain_sans_framework =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS)
          .allowEmptyShould(true)
          .as("domain ne dépend d'aucun framework")
          .because(
              "ADR-001 : le domaine reste du pur Java (ni Spring, ni jakarta, ni gRPC, ni protobuf,"
                  + " ni Jackson)");

  @ArchTest
  static final ArchRule application_sans_framework =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(FRAMEWORKS)
          .allowEmptyShould(true)
          .as("application ne dépend d'aucun framework")
          .because(
              "ADR-001 : les cas d'usage et ports restent du pur Java (ni Spring, ni jakarta, ni"
                  + " gRPC, ni protobuf, ni Jackson)");

  // (2) Sens des dépendances : domain <- application <- adaptateurs

  @ArchTest
  static final ArchRule domain_independant_du_reste =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(APPLICATION, ADAPTER, BATCH, SECURITY, CONFIG)
          .allowEmptyShould(true)
          .as("domain ne dépend ni de application, ni des adaptateurs, ni de batch/security/config")
          .because("ADR-001 : le domaine est au centre et ne connaît aucune couche extérieure");

  @ArchTest
  static final ArchRule application_independante_des_couches_externes =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER, BATCH, SECURITY, CONFIG)
          .allowEmptyShould(true)
          .as("application ne dépend ni des adaptateurs, ni de batch, security ou config")
          .because("ADR-001 : les adaptateurs dépendent de application, jamais l'inverse");

  // (3) Adaptateurs isolés les uns des autres

  @ArchTest
  static final ArchRule adaptateur_grpc_isole =
      noClasses()
          .that()
          .resideInAPackage(IN_GRPC)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(IN_REST, ADAPTER_OUT)
          .allowEmptyShould(true)
          .as("adapter.in.grpc ne dépend ni de adapter.in.rest ni d'adapter.out")
          .because("ADR-001 : les adaptateurs ne se dépendent pas entre eux");

  @ArchTest
  static final ArchRule adaptateur_rest_isole =
      noClasses()
          .that()
          .resideInAPackage(IN_REST)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(IN_GRPC, ADAPTER_OUT)
          .allowEmptyShould(true)
          .as("adapter.in.rest ne dépend ni de adapter.in.grpc ni d'adapter.out")
          .because("ADR-001 : les adaptateurs ne se dépendent pas entre eux");

  @ArchTest
  static final ArchRule adaptateurs_sortants_independants_des_entrants =
      noClasses()
          .that()
          .resideInAPackage(ADAPTER_OUT)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER_IN)
          .allowEmptyShould(true)
          .as("adapter.out ne dépend d'aucun adaptateur entrant")
          .because("ADR-001 : les adaptateurs ne se dépendent pas entre eux");

  @ArchTest
  static final ArchRule adaptateurs_sortants_independants_entre_eux =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".adapter.out.persistence..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ROOT + ".adapter.out.audit..")
          .allowEmptyShould(true)
          .as("adapter.out.persistence ne dépend pas d'adapter.out.audit")
          .because("ADR-001 : les adaptateurs ne se dépendent pas entre eux");

  @ArchTest
  static final ArchRule adaptateur_audit_independant_de_persistence =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".adapter.out.audit..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ROOT + ".adapter.out.persistence..")
          .allowEmptyShould(true)
          .as("adapter.out.audit ne dépend pas d'adapter.out.persistence")
          .because("ADR-001 : les adaptateurs ne se dépendent pas entre eux");

  // (4) batch et security

  @ArchTest
  static final ArchRule batch_dependances_limitees =
      noClasses()
          .that()
          .resideInAPackage(BATCH)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER, SECURITY, CONFIG)
          .orShould()
          .dependOnClassesThat(
              com.tngtech.archunit.base.DescribedPredicate.and(
                  com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                      APPLICATION),
                  com.tngtech.archunit.base.DescribedPredicate.not(
                      com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(
                          APPLICATION_PORT))))
          .allowEmptyShould(true)
          .as("batch ne dépend que de domain, application.port et du framework")
          .because(
              "ADR-001 : batch passe par les ports, jamais par les adaptateurs ni par les services"
                  + " applicatifs concrets");

  @ArchTest
  static final ArchRule security_sans_adaptateurs =
      noClasses()
          .that()
          .resideInAPackage(SECURITY)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ADAPTER)
          .allowEmptyShould(true)
          .as("security ne dépend d'aucun adaptateur (domain autorisé : Acteur, Role)")
          .because("ADR-001/ADR-002 : les adaptateurs utilisent security, pas l'inverse");

  // (5) Pas de cycles entre packages de premier niveau

  @ArchTest
  static final ArchRule pas_de_cycle_entre_packages_de_premier_niveau =
      slices()
          .matching(ROOT + ".(*)..")
          .should()
          .beFreeOfCycles()
          .allowEmptyShould(true)
          .because("ADR-001 : le graphe de dépendances entre packages racine doit rester acyclique");
}
