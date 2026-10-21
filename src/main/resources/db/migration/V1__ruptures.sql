-- V1 : ruptures de médicaments et tables associées.
-- SQL volontairement restreint au sous-ensemble commun H2 (mode PostgreSQL) / PostgreSQL.
-- La validation fine (formats complets) reste dans le domaine ; la base garde les invariants simples.

CREATE TABLE ruptures (
    id                  UUID                     NOT NULL PRIMARY KEY,
    code_produit        VARCHAR(10)              NOT NULL,
    declarant           VARCHAR(10)              NOT NULL,
    date_debut          DATE                     NOT NULL,
    date_fin_estimee    DATE,
    date_fin_effective  DATE,
    motif               VARCHAR(20)              NOT NULL,
    statut              VARCHAR(20)              NOT NULL,
    -- verrouillage optimiste, géré par l'adaptateur de persistance
    version             BIGINT                   NOT NULL DEFAULT 0,
    cree_le             TIMESTAMP WITH TIME ZONE NOT NULL,
    modifie_le          TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_ruptures_code_produit CHECK (code_produit LIKE 'VMP-%'),
    CONSTRAINT ck_ruptures_declarant CHECK (declarant LIKE 'PRO-%'),
    CONSTRAINT ck_ruptures_motif CHECK (motif IN ('PRODUCTION', 'LOGISTIQUE', 'REGLEMENTAIRE', 'DEMANDE')),
    CONSTRAINT ck_ruptures_statut CHECK (statut IN ('EN_COURS', 'CLOTUREE')),
    CONSTRAINT ck_ruptures_statut_fin CHECK (
        (statut = 'EN_COURS' AND date_fin_effective IS NULL)
        OR (statut = 'CLOTUREE' AND date_fin_effective IS NOT NULL)),
    CONSTRAINT ck_ruptures_fin_estimee CHECK (date_fin_estimee IS NULL OR date_fin_estimee >= date_debut),
    CONSTRAINT ck_ruptures_fin_effective CHECK (date_fin_effective IS NULL OR date_fin_effective >= date_debut)
);

CREATE INDEX idx_ruptures_statut_debut ON ruptures (statut, date_debut);
CREATE INDEX idx_ruptures_code_produit ON ruptures (code_produit);
CREATE INDEX idx_ruptures_declarant ON ruptures (declarant);

CREATE TABLE rupture_regions (
    rupture_id  UUID        NOT NULL,
    region      VARCHAR(40) NOT NULL,
    CONSTRAINT pk_rupture_regions PRIMARY KEY (rupture_id, region),
    CONSTRAINT fk_rupture_regions_rupture FOREIGN KEY (rupture_id) REFERENCES ruptures (id) ON DELETE CASCADE,
    CONSTRAINT ck_rupture_regions_region CHECK (region IN (
        'HAUTE_PAPERASSE', 'BASSE_VALIDATION', 'PLATEAU_DU_LEGACY',
        'COTE_DU_TIMEOUT', 'MARAIS_DE_LA_RECETTE', 'PROVINCE_DU_TICKET_OUVERT'))
);

CREATE INDEX idx_rupture_regions_region ON rupture_regions (region);

CREATE TABLE rupture_alternatives (
    rupture_id          UUID         NOT NULL,
    produit_alternatif  VARCHAR(10)  NOT NULL,
    commentaire         VARCHAR(280) NOT NULL DEFAULT '',
    CONSTRAINT pk_rupture_alternatives PRIMARY KEY (rupture_id, produit_alternatif),
    CONSTRAINT fk_rupture_alternatives_rupture FOREIGN KEY (rupture_id) REFERENCES ruptures (id) ON DELETE CASCADE
);
