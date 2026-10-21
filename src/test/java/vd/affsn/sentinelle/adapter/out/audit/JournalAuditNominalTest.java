package vd.affsn.sentinelle.adapter.out.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import vd.affsn.sentinelle.application.port.out.AuditPort;
import vd.affsn.sentinelle.application.port.out.EvenementAudit;
import vd.affsn.sentinelle.application.port.out.TypeEvenementAudit;
import vd.affsn.sentinelle.domain.model.Acteur;
import vd.affsn.sentinelle.domain.model.RuptureId;

/** Comportement nominal : écriture, chaînage, recalcul indépendant, aller-retour base, assainissement. */
class JournalAuditNominalTest extends AuditTestSupport {

    @Test
    void premiereEntreeChaineeSurLaGeneseParDefaut() {
        auditPort.enregistrer(evenement("produit VMP-123456, 2 régions"));

        List<Ligne> l = lignes();
        assertThat(l).hasSize(1);
        assertThat(l.get(0).hashPrecedent()).isEqualTo(GENESE_DEFAUT);
        assertThat(l.get(0).hashCourant()).matches("[0-9a-f]{64}");
    }

    @Test
    void premiereEntreeChaineeSurLaGeneseConfiguree() {
        String genese = "ab".repeat(32);
        AuditProperties p = props(genese, null, null);
        nouvelAdaptateur(p).enregistrer(evenement("x"));

        assertThat(lignes().get(0).hashPrecedent()).isEqualTo(genese);
        assertThat(verificateur(p).verifier().integre()).isTrue();
        // La même chaîne relue avec la genèse par défaut est rompue dès la tête.
        assertThat(verificateur.verifier().natureRupture())
                .contains(ResultatVerificationIntegrite.NatureRupture.CHAINAGE_ROMPU);
    }

    @Test
    void chaineDeNEntreesSequenceCroissanteEtChainage() {
        ecrire(auditPort, 12);

        List<Ligne> l = lignes();
        assertThat(l).hasSize(12);
        assertThat(l.get(0).hashPrecedent()).isEqualTo(GENESE_DEFAUT);
        for (int i = 1; i < l.size(); i++) {
            assertThat(l.get(i).sequence()).isGreaterThan(l.get(i - 1).sequence());
            assertThat(l.get(i).hashPrecedent()).isEqualTo(l.get(i - 1).hashCourant());
        }
        assertThat(l.stream().map(Ligne::hashPrecedent).distinct()).hasSize(12);
    }

    @Test
    void verificateurSurJournalSainIntegre() {
        ecrire(auditPort, 7);
        List<Ligne> l = lignes();

        ResultatVerificationIntegrite r = verificateur.verifier();

        assertThat(r.integre()).isTrue();
        assertThat(r.entreesVerifiees()).isEqualTo(7);
        assertThat(r.premiereRupture()).isEmpty();
        assertThat(r.natureRupture()).isEmpty();
        assertThat(r.derniereSequenceVerifiee()).isEqualTo(l.get(6).sequence());
        assertThat(r.dernierHashVerifie()).isEqualTo(l.get(6).hashCourant());
    }

    @Test
    void recalculIndependantDuHashDepuisLaJavadocV1() {
        auditPort.enregistrer(evenement("produit VMP-123456, 2 régions, «ç» 😀"));
        auditPort.enregistrer(evenement(TypeEvenementAudit.CLOTURE, Instant.parse("1969-12-31T23:59:59.000001Z"),
                "a=1:b\\n"));
        auditPort.enregistrer(evenement(TypeEvenementAudit.MODIFICATION, Instant.parse("2026-01-01T00:00:00Z"), "z"));

        for (Ligne l : lignes()) {
            assertThat(hashIndependant(l)).as("séquence %d", l.sequence()).isEqualTo(l.hashCourant());
        }
    }

    @Test
    void instantTronqueALaMicrosecondeMemeHashApresRelecture() {
        Instant avecNanos = Instant.parse("2026-10-21T08:15:30.123456789Z");
        auditPort.enregistrer(evenement(TypeEvenementAudit.DECLARATION, avecNanos, "nanos"));

        Ligne l = lignes().get(0);
        assertThat(l.instant().toInstant()).isEqualTo(Instant.parse("2026-10-21T08:15:30.123456Z"));
        assertThat(instantCanonique(l.instant().toInstant())).isEqualTo("2026-10-21T08:15:30.123456Z");
        assertThat(hashIndependant(l)).isEqualTo(l.hashCourant());
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void instantSansFractionToujoursSixDecimales() {
        auditPort.enregistrer(evenement(TypeEvenementAudit.DECLARATION, Instant.parse("2026-10-21T08:15:30Z"), "s"));
        Ligne l = lignes().get(0);
        // Si le format omettait les décimales, ce recalcul (« .000000Z ») divergerait.
        assertThat(hashIndependant(l.evenementId().toString(), l.type(), l.ruptureId().toString(), l.sujet(),
                l.role(), "2026-10-21T08:15:30.000000Z", l.resume(), l.hashPrecedent())).isEqualTo(l.hashCourant());
    }

    @Test
    void valeursStockeesFideles() {
        RuptureId rupture = RuptureId.generer();
        EvenementAudit e = new EvenementAudit(TypeEvenementAudit.MODIFICATION, rupture,
                Acteur.prescripteur("presc-1"), INSTANT, "  résumé  ");
        auditPort.enregistrer(e);

        Ligne l = lignes().get(0);
        assertThat(l.type()).isEqualTo("MODIFICATION");
        assertThat(l.ruptureId()).isEqualTo(rupture.valeur());
        assertThat(l.sujet()).isEqualTo("presc-1");
        assertThat(l.role()).isEqualTo("PRESCRIPTEUR");
        assertThat(l.resume()).isEqualTo("résumé");
    }

    @Test
    void evenementIdUniqueEtDifferentDeLaRupture() {
        EvenementAudit memeEvenement = evenement("même");
        for (int i = 0; i < 20; i++) {
            auditPort.enregistrer(memeEvenement);
        }
        List<Ligne> l = lignes();
        Set<UUID> ids = l.stream().map(Ligne::evenementId).collect(Collectors.toSet());
        assertThat(ids).hasSize(20).doesNotContain(memeEvenement.ruptureId().valeur());
        // Le même événement soumis deux fois produit deux entrées distinctes (pas d'idempotence).
        assertThat(l.stream().map(Ligne::hashCourant).distinct()).hasSize(20);
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void tousLesTypesDEvenementSontAcceptesParLeSchema() {
        for (TypeEvenementAudit t : TypeEvenementAudit.values()) {
            auditPort.enregistrer(evenement(t, INSTANT, t.name()));
        }
        assertThat(lignes()).extracting(Ligne::type)
                .containsExactly("DECLARATION", "MODIFICATION", "CLOTURE");
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void resumeAssainiAvantStockageEtHache() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhZ2VudCJ9.c2lnbmF0dXJl";
        auditPort.enregistrer(evenement("jeton " + jwt + " fin"));
        auditPort.enregistrer(evenement("entete Bearer abc.DEF-123 fin"));
        auditPort.enregistrer(evenement("entete bEaReR    xyz"));
        auditPort.enregistrer(evenement("a‮b​c d e﻿f"));
        auditPort.enregistrer(evenement("   bords   "));

        assertThat(lignes()).extracting(Ligne::resume).containsExactly(
                "jeton [masqué] fin",
                "entete [masqué] fin",
                "entete [masqué]",
                "a b c d e f",
                "bords");
        for (Ligne l : lignes()) {
            assertThat(hashIndependant(l)).isEqualTo(l.hashCourant());
        }
        assertThat(verificateur.verifier().integre()).isTrue();
    }

    @Test
    void resumeDeLongueurMaximaleAvecEmojiStockeEtVerifie() {
        String resume = "a".repeat(498) + "😀"; // 500 unités UTF-16, 502 octets UTF-8
        auditPort.enregistrer(evenement(resume));
        Ligne l = lignes().get(0);
        assertThat(l.resume()).isEqualTo(resume);
        assertThat(hashIndependant(l)).isEqualTo(l.hashCourant());
    }

    @Test
    void deuxAdaptateursIndependantsPartagentLaChaineSequentiellement() {
        AuditPort autre = nouvelAdaptateur(props(null, Duration.ofSeconds(2), null));
        auditPort.enregistrer(evenement("a"));
        autre.enregistrer(evenement("b"));
        auditPort.enregistrer(evenement("c"));
        assertThat(verificateur.verifier().integre()).isTrue();
        assertThat(verificateur.verifier().entreesVerifiees()).isEqualTo(3);
    }
}
