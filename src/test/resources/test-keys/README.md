# Clés de test JWT

**CLÉS DE TEST GÉNÉRÉES POUR LA DÉMO – NE PROTÈGENT RIEN – NE JAMAIS UTILISER AILLEURS**

- `TEST-ONLY-jwt-private.pem` : clé privée RSA 2048 (PKCS#8), sert uniquement à signer des jetons
  dans les tests (`JwtTestFactory`). Sentinelle ne signe jamais de jetons.
- `TEST-ONLY-jwt-public.pem` : clé publique correspondante (SubjectPublicKeyInfo), chargée par le
  profil `test` (`config/security.yml`).

Ces fichiers sont publics par construction (committés dans le dépôt) : toute réutilisation hors de
`src/test` est une faute à signaler (ADR-002). En production, la clé publique est fournie par
l'environnement (`SENTINELLE_JWT_PUBLIC_KEY_LOCATION`) et il n'existe aucune clé de repli.

Émetteur et audience de test : `https://emetteur-de-test.affsn.invalid` et
`https://sentinelle-de-test.affsn.invalid` (domaine `.invalid`, RFC 2606).

Régénération (openssl) :

```sh
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out TEST-ONLY-jwt-private.pem
openssl pkey -in TEST-ONLY-jwt-private.pem -pubout -out TEST-ONLY-jwt-public.pem
```

puis remettre la ligne d'avertissement `TEST ONLY` en tête de chaque fichier (texte hors bloc PEM,
ignoré par les lecteurs PEM).
