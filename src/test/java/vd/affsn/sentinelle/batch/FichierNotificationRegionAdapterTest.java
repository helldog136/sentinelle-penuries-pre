package vd.affsn.sentinelle.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.Notification;
import vd.affsn.sentinelle.application.port.out.NotificationRegionPort.RegionEnTension;
import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/** Adaptateur fichier : format JSON exact, nommage, écriture atomique, garde-fous de chemin. */
class FichierNotificationRegionAdapterTest {

    private static final LocalDate D = LocalDate.of(2026, 10, 21);
    private static final String NOM_D = "notifications-tension-2026-10-21.json";

    @TempDir
    Path tmp;

    private static Notification notification(LocalDate date, double seuil, RegionEnTension... regions) {
        return new Notification(date, new SeuilTension(seuil), List.of(regions));
    }

    private static String lire(Path fichier) throws IOException {
        return Files.readString(fichier, StandardCharsets.UTF_8);
    }

    private static List<String> contenuRepertoire(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Nested
    class FormatEtNommage {

        @Test
        void formatJsonExactCompactEtOrdreDesChamps() throws IOException {
            var adapter = new FichierNotificationRegionAdapter(tmp);
            adapter.publier(notification(D, 10.0, new RegionEnTension(Region.HAUTE_PAPERASSE, 12.5)));

            assertThat(lire(tmp.resolve(NOM_D))).isEqualTo(
                    "{\"dateReference\":\"2026-10-21\",\"seuil\":10.0,"
                            + "\"regions\":[{\"region\":\"HAUTE_PAPERASSE\",\"indice\":12.5}]}");
        }

        @Test
        void regionsRetrieesDansLOrdreDeLEnumQuelQueSoitLOrdreRecu() throws IOException {
            var adapter = new FichierNotificationRegionAdapter(tmp);
            adapter.publier(notification(D, 1.0,
                    new RegionEnTension(Region.PROVINCE_DU_TICKET_OUVERT, 6.0),
                    new RegionEnTension(Region.HAUTE_PAPERASSE, 2.0),
                    new RegionEnTension(Region.COTE_DU_TIMEOUT, 4.0),
                    new RegionEnTension(Region.BASSE_VALIDATION, 3.0)));

            assertThat(lire(tmp.resolve(NOM_D))).isEqualTo(
                    "{\"dateReference\":\"2026-10-21\",\"seuil\":1.0,\"regions\":["
                            + "{\"region\":\"HAUTE_PAPERASSE\",\"indice\":2.0},"
                            + "{\"region\":\"BASSE_VALIDATION\",\"indice\":3.0},"
                            + "{\"region\":\"COTE_DU_TIMEOUT\",\"indice\":4.0},"
                            + "{\"region\":\"PROVINCE_DU_TICKET_OUVERT\",\"indice\":6.0}]}");
        }

        @Test
        void listeVideEcriteQuandRienASignaler() throws IOException {
            new FichierNotificationRegionAdapter(tmp).publier(notification(D, 0.0));

            assertThat(lire(tmp.resolve(NOM_D)))
                    .isEqualTo("{\"dateReference\":\"2026-10-21\",\"seuil\":0.0,\"regions\":[]}");
        }

        @Test
        void indiceNonDecimalExactEcritAvecToutesSesDecimales() throws IOException {
            new FichierNotificationRegionAdapter(tmp).publier(
                    notification(D, 2.0, new RegionEnTension(Region.MARAIS_DE_LA_RECETTE, 1 + 10 / 7.0)));

            assertThat(lire(tmp.resolve(NOM_D))).isEqualTo(
                    "{\"dateReference\":\"2026-10-21\",\"seuil\":2.0,\"regions\":"
                            + "[{\"region\":\"MARAIS_DE_LA_RECETTE\",\"indice\":" + (1 + 10 / 7.0) + "}]}");
        }

        @ParameterizedTest
        @CsvSource({
            "2026-10-21, notifications-tension-2026-10-21.json",
            "0000-01-01, notifications-tension-0000-01-01.json",
            "0999-03-04, notifications-tension-0999-03-04.json",
            "9999-12-31, notifications-tension-9999-12-31.json",
            "2028-02-29, notifications-tension-2028-02-29.json"})
        void nomDeriveUniquementDeLaDate(LocalDate date, String attendu) {
            assertThat(FichierNotificationRegionAdapter.nomFichier(date)).isEqualTo(attendu);
        }

        @Test
        void anneeHorsPlageAAAARefusee() {
            assertThatThrownBy(() -> FichierNotificationRegionAdapter.nomFichier(LocalDate.of(10000, 1, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FichierNotificationRegionAdapter.nomFichier(LocalDate.of(-1, 1, 1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void publicationAnneeHorsPlageNEcritRien() throws IOException {
            var adapter = new FichierNotificationRegionAdapter(tmp);
            assertThatThrownBy(() -> adapter.publier(notification(LocalDate.of(10000, 1, 1), 1.0)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(contenuRepertoire(tmp)).isEmpty();
        }

        @Test
        void fichierCreeEnRwRSurPosix() throws IOException {
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
            new FichierNotificationRegionAdapter(tmp).publier(notification(D, 1.0));

            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(tmp.resolve(NOM_D))))
                    .isEqualTo("rw-r-----");
        }

        @Test
        void notificationNulleRefusee() {
            assertThatThrownBy(() -> new FichierNotificationRegionAdapter(tmp).publier(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class Repertoire {

        @Test
        void repertoireRelatifRefuseALaConstruction() {
            assertThatThrownBy(() -> new FichierNotificationRegionAdapter(Path.of("notifications")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chemin absolu");
            assertThatThrownBy(() -> new FichierNotificationRegionAdapter(Path.of("../notifications")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void repertoireNulRefuse() {
            assertThatThrownBy(() -> new FichierNotificationRegionAdapter(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void repertoireAbsentEstCree() throws IOException {
            // Comportement actuel (spécification muette) : les répertoires manquants sont créés.
            Path absent = tmp.resolve("a").resolve("b");
            new FichierNotificationRegionAdapter(absent).publier(notification(D, 1.0));

            assertThat(absent.resolve(NOM_D)).isRegularFile();
        }

        @Test
        void repertoireQuiEstUnFichierEchoueSansRienEcrire() throws IOException {
            Path fichier = Files.writeString(tmp.resolve("pas-un-repertoire"), "x");
            var adapter = new FichierNotificationRegionAdapter(fichier);

            assertThatThrownBy(() -> adapter.publier(notification(D, 1.0)))
                    .isInstanceOf(UncheckedIOException.class);
            assertThat(contenuRepertoire(tmp)).containsExactly("pas-un-repertoire");
        }

        @Test
        void repertoireSousUnFichierEchoue() throws IOException {
            Path fichier = Files.writeString(tmp.resolve("fichier"), "x");
            var adapter = new FichierNotificationRegionAdapter(fichier.resolve("sous"));

            assertThatThrownBy(() -> adapter.publier(notification(D, 1.0)))
                    .isInstanceOf(UncheckedIOException.class);
        }

        @Test
        void repertoireNonInscriptibleEchoueSansFichierTemporaire() throws IOException {
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
            Path lectureSeule = Files.createDirectory(tmp.resolve("lecture-seule"));
            Files.setPosixFilePermissions(lectureSeule, PosixFilePermissions.fromString("r-xr-xr-x"));
            try {
                // En root, les permissions POSIX ne bloquent pas l'écriture : test sans objet.
                assumeFalse(Files.isWritable(lectureSeule), "exécution en root : permissions ignorées");
                var adapter = new FichierNotificationRegionAdapter(lectureSeule);

                assertThatThrownBy(() -> adapter.publier(notification(D, 1.0)))
                        .isInstanceOf(UncheckedIOException.class);
                assertThat(contenuRepertoire(lectureSeule)).isEmpty();
            } finally {
                Files.setPosixFilePermissions(lectureSeule, PosixFilePermissions.fromString("rwxr-xr-x"));
            }
        }

        @Test
        void cheminAvecPointPointNormaliseSousLeRepertoireConfigure() throws IOException {
            Path cible = Files.createDirectory(tmp.resolve("cible"));
            Files.createDirectory(tmp.resolve("autre"));
            var adapter = new FichierNotificationRegionAdapter(tmp.resolve("autre").resolve("..").resolve("cible"));
            adapter.publier(notification(D, 1.0));

            assertThat(contenuRepertoire(cible)).containsExactly(NOM_D);
            assertThat(contenuRepertoire(tmp.resolve("autre"))).isEmpty();
        }

        @Test
        void repertoireLienSymboliqueEcritDansLaCibleReelle() throws IOException {
            Path reel = Files.createDirectory(tmp.resolve("reel"));
            Path lien;
            try {
                lien = Files.createSymbolicLink(tmp.resolve("lien"), reel);
            } catch (UnsupportedOperationException | IOException e) {
                assumeTrue(false, "liens symboliques non supportés");
                return;
            }
            new FichierNotificationRegionAdapter(lien).publier(notification(D, 1.0));

            assertThat(contenuRepertoire(reel)).containsExactly(NOM_D);
        }

        @Test
        void seulLeFichierDuJourEstCreeAucunTemporaireResiduel() throws IOException {
            var adapter = new FichierNotificationRegionAdapter(tmp);
            adapter.publier(notification(D, 1.0));
            adapter.publier(notification(D.plusDays(1), 1.0));

            assertThat(contenuRepertoire(tmp))
                    .containsExactly(NOM_D, "notifications-tension-2026-10-22.json");
        }
    }

    @Nested
    class EcritureAtomique {

        @Test
        void remplacementDUnFichierExistantParRenommageEtNonParReecritureEnPlace() throws IOException {
            assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
            Path cible = tmp.resolve(NOM_D);
            String ancien = "{\"ancien\":\"contenu volontairement plus long que le nouveau pour detecter une troncature\"}";
            Files.writeString(cible, ancien);
            Object inodeAvant = Files.getAttribute(cible, "unix:ino");

            // Un lecteur qui a ouvert l'ancien fichier avant la publication...
            try (FileChannel lecteur = FileChannel.open(cible, StandardOpenOption.READ)) {
                new FichierNotificationRegionAdapter(tmp).publier(notification(D, 1.0));

                // ... continue de lire l'ancien contenu COMPLET : il n'a pas été tronqué en place.
                ByteBuffer tampon = ByteBuffer.allocate(1024);
                lecteur.read(tampon, 0);
                tampon.flip();
                assertThat(StandardCharsets.UTF_8.decode(tampon).toString()).isEqualTo(ancien);
            }

            assertThat(Files.getAttribute(cible, "unix:ino")).isNotEqualTo(inodeAvant);
            assertThat(lire(cible)).isEqualTo("{\"dateReference\":\"2026-10-21\",\"seuil\":1.0,\"regions\":[]}");
            assertThat(contenuRepertoire(tmp)).containsExactly(NOM_D);
        }

        @Test
        void republicationMemeDateRemplaceLeContenu() throws IOException {
            var adapter = new FichierNotificationRegionAdapter(tmp);
            adapter.publier(notification(D, 1.0, new RegionEnTension(Region.HAUTE_PAPERASSE, 2.0)));
            adapter.publier(notification(D, 1.0));

            assertThat(lire(tmp.resolve(NOM_D)))
                    .isEqualTo("{\"dateReference\":\"2026-10-21\",\"seuil\":1.0,\"regions\":[]}");
            assertThat(contenuRepertoire(tmp)).containsExactly(NOM_D);
        }

        @Test
        void echecDuDeplacementNeLaissePasDeTemporaireNiNAlterLaCible() throws IOException {
            // La cible est un répertoire non vide : le renommage final échoue.
            Path cible = Files.createDirectory(tmp.resolve(NOM_D));
            Files.writeString(cible.resolve("garde"), "x");
            var adapter = new FichierNotificationRegionAdapter(tmp);

            assertThatThrownBy(() -> adapter.publier(notification(D, 1.0)))
                    .isInstanceOf(UncheckedIOException.class);
            assertThat(contenuRepertoire(tmp)).containsExactly(NOM_D);
            assertThat(cible).isDirectory();
            assertThat(contenuRepertoire(cible)).containsExactly("garde");
        }

        @Test
        void cibleLienSymboliqueRemplaceeSansEcrireDansSaDestination() throws IOException {
            // Si le nom du jour est un lien vers un fichier hors du répertoire, c'est le lien qui est
            // remplacé (renommage), la destination du lien n'est jamais écrite.
            Path dehors = Files.createDirectory(tmp.resolve("dehors"));
            Path victime = Files.writeString(dehors.resolve("victime.txt"), "intact");
            Path base = Files.createDirectory(tmp.resolve("base"));
            try {
                Files.createSymbolicLink(base.resolve(NOM_D), victime);
            } catch (UnsupportedOperationException | IOException e) {
                assumeTrue(false, "liens symboliques non supportés");
            }
            new FichierNotificationRegionAdapter(base).publier(notification(D, 1.0));

            assertThat(lire(victime)).isEqualTo("intact");
            assertThat(Files.isSymbolicLink(base.resolve(NOM_D))).isFalse();
            assertThat(lire(base.resolve(NOM_D))).startsWith("{\"dateReference\":\"2026-10-21\"");
        }
    }

    @Nested
    class AmbiguitesDocumentees {

        /**
         * Un fichier temporaire laissé par un arrêt brutal antérieur (JVM tuée entre la création du
         * temporaire et le renommage) n'est jamais nettoyé par les publications suivantes.
         */
        @Test
        void temporaireOrphelinDUnArretBrutalPrecedentConserve() throws IOException {
            Files.writeString(tmp.resolve(".notifications-tension-123.tmp"), "{\"partiel");
            new FichierNotificationRegionAdapter(tmp).publier(notification(D, 1.0));

            assertThat(contenuRepertoire(tmp)).containsExactly(".notifications-tension-123.tmp", NOM_D);
        }

        /** {@code -0.0} est un seuil accepté (>= 0) et s'écrit tel quel. */
        @Test
        void seuilMoinsZeroEcritTelQuel() throws IOException {
            new FichierNotificationRegionAdapter(tmp).publier(notification(D, -0.0));

            assertThat(lire(tmp.resolve(NOM_D)))
                    .isEqualTo("{\"dateReference\":\"2026-10-21\",\"seuil\":-0.0,\"regions\":[]}");
        }
    }
}
