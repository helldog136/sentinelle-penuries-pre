-- V4 : référentiel des produits essentiels.
-- Table de référence indépendante : pas de clé étrangère vers ruptures (le référentiel vit à son rythme).
-- depuis : date de classement du produit comme essentiel (valeur fictive, schéma seul, aucune donnée ici).
-- SQL volontairement restreint au sous-ensemble commun H2 (mode PostgreSQL) / PostgreSQL.

CREATE TABLE produits_essentiels (
    code_produit  VARCHAR(10) NOT NULL PRIMARY KEY,
    depuis        DATE        NOT NULL,
    CONSTRAINT ck_produits_essentiels_code_produit CHECK (code_produit LIKE 'VMP-%')
);
