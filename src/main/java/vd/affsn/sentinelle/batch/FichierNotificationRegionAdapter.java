package vd.affsn.sentinelle.batch;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import vd.affsn.sentinelle.application.port.out.NotificationRegionPort;

/**
 * Implémentation fichier de {@link NotificationRegionPort} : écrit
 * {@code <repertoire>/notifications-tension-AAAA-MM-JJ.json}.
 *
 * <p>Format :
 * <pre>{@code
 * {"dateReference":"2026-10-21","seuil":10.0,"regions":[{"region":"HAUTE_PAPERASSE","indice":12.5}]}
 * }</pre>
 * {@code regions} est triée dans l'ordre de déclaration de l'enum {@code Region} et ne contient
 * que les régions au-dessus du seuil. <strong>Décision :</strong> le fichier est écrit même si
 * aucune région n'est signalée (liste vide), afin de distinguer « exécuté, rien à signaler » de
 * « non exécuté » ; une réexécution pour la même date remplace le fichier.
 *
 * <p>Sécurité et robustesse :
 * <ul>
 *   <li>le nom de fichier est construit UNIQUEMENT à partir de la date, puis vérifié par
 *       expression régulière ; le chemin cible est normalisé et doit avoir pour parent exact le
 *       répertoire configuré (pas de traversée de chemin) ;</li>
 *   <li>écriture atomique : fichier temporaire dans le même répertoire, {@code fsync}, puis
 *       {@link StandardCopyOption#ATOMIC_MOVE} ; si le système de fichiers ne supporte pas le
 *       déplacement atomique, l'écriture échoue (pas de repli non atomique) ;</li>
 *   <li>sur POSIX, le fichier est créé en {@code rw-r-----}.</li>
 * </ul>
 */
public final class FichierNotificationRegionAdapter implements NotificationRegionPort {

    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern NOM_ATTENDU = Pattern.compile("notifications-tension-\\d{4}-\\d{2}-\\d{2}\\.json");

    private final Path repertoire;
    private final ObjectMapper json = new ObjectMapper().disable(SerializationFeature.INDENT_OUTPUT);

    public FichierNotificationRegionAdapter(Path repertoire) {
        Objects.requireNonNull(repertoire, "repertoire");
        if (!repertoire.isAbsolute()) {
            throw new IllegalArgumentException("Répertoire de notifications : chemin absolu attendu");
        }
        this.repertoire = repertoire.normalize();
    }

    @Override
    public void publier(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        try {
            Path base = Files.createDirectories(repertoire).toRealPath();
            Path cible = cheminCible(base, notification.dateReference());
            byte[] contenu = json.writeValueAsBytes(versJson(notification));
            ecrireAtomiquement(base, cible, contenu);
        } catch (IOException e) {
            throw new UncheckedIOException("Échec d'écriture du fichier de notifications de tension", e);
        }
    }

    /** Visible pour diagnostic : nom de fichier déterministe pour une date. */
    static String nomFichier(LocalDate date) {
        int annee = date.getYear();
        if (annee < 0 || annee > 9999) {
            throw new IllegalArgumentException("Date de référence hors de la plage AAAA");
        }
        String nom = "notifications-tension-" + FORMAT_DATE.format(date) + ".json";
        if (!NOM_ATTENDU.matcher(nom).matches()) {
            throw new IllegalArgumentException("Nom de fichier de notifications inattendu");
        }
        return nom;
    }

    private static Path cheminCible(Path base, LocalDate date) {
        Path cible = base.resolve(nomFichier(date)).normalize();
        if (!base.equals(cible.getParent()) || !cible.startsWith(base)) {
            throw new IllegalStateException("Chemin de notification hors du répertoire configuré");
        }
        return cible;
    }

    private static Map<String, Object> versJson(Notification notification) {
        List<Map<String, Object>> regions = notification.regions().stream()
                .sorted(Comparator.comparing(RegionEnTension::region))
                .map(r -> {
                    Map<String, Object> entree = new LinkedHashMap<>();
                    entree.put("region", r.region().name());
                    entree.put("indice", r.indice());
                    return entree;
                })
                .toList();
        Map<String, Object> racine = new LinkedHashMap<>();
        racine.put("dateReference", FORMAT_DATE.format(notification.dateReference()));
        racine.put("seuil", notification.seuil().valeur());
        racine.put("regions", regions);
        return racine;
    }

    private static void ecrireAtomiquement(Path base, Path cible, byte[] contenu) throws IOException {
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        Path temporaire = posix
                ? Files.createTempFile(base, ".notifications-tension-", ".tmp",
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-r-----")))
                : Files.createTempFile(base, ".notifications-tension-", ".tmp");
        try {
            try (FileChannel canal = FileChannel.open(temporaire, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer tampon = ByteBuffer.wrap(contenu);
                while (tampon.hasRemaining()) {
                    canal.write(tampon);
                }
                canal.force(true);
            }
            try {
                Files.move(temporaire, cible, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Déplacement atomique non supporté par le système de fichiers", e);
            }
        } finally {
            Files.deleteIfExists(temporaire);
        }
    }
}
