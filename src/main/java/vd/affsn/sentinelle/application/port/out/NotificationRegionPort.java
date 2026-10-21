package vd.affsn.sentinelle.application.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import vd.affsn.sentinelle.domain.model.Region;
import vd.affsn.sentinelle.domain.tension.SeuilTension;

/** Publication (fichier de notifications) des régions dont l'indice dépasse strictement le seuil. */
public interface NotificationRegionPort {

    void publier(Notification notification);

    record RegionEnTension(Region region, double indice) {
        public RegionEnTension {
            Objects.requireNonNull(region, "region");
        }
    }

    /** {@code regions} peut être vide : le fichier est alors écrit sans entrée. */
    record Notification(LocalDate dateReference, SeuilTension seuil, List<RegionEnTension> regions) {
        public Notification {
            Objects.requireNonNull(dateReference, "dateReference");
            Objects.requireNonNull(seuil, "seuil");
            regions = List.copyOf(regions);
        }
    }
}
