# Tarifs API Anthropic (USD/MTok), 2026-10-22
Source principale: https://platform.claude.com/docs/en/about-claude/pricing.md (WebFetch 2026-10-22). Secondaire: skill claude-api (cache 2026-10-06) + shared/prompt-caching.md.
| Modele | Input | Cache write 5m | Cache write 1h | Cache read | Output | Batch in/out |
|---|---|---|---|---|---|---|
| Haiku 5.5 (prompt <=100K) | 0.10 | 0.125 | 0.20 | 0.01 | 0.50 | 0.05/0.25 |
| Haiku 5.5 (prompt >100K) | 0.50 | 0.625 | 1.00 | 0.05 | 2.50 | 0.25/1.25 |
| Sonnet 5.5 | 2 | 2.50 | 4 | 0.10 | 10 | 1/5 |
| Opus 5.5 (alias opus, confirme par le skill: defaut=claude-opus-5-5) | 4 | 5 | 8 | 0.20 | 20 | 2/10 |
| Fable 5.1 | 10 | 12.50 | 20 | 0.25 | 50 | 5/25 |
Divergence: cache read Sonnet 5.5 = 0.20 (skill) vs 0.10 (page, note 2: 0.05x). Retenu 0.10.
Contexte long: seul Haiku 5.5 a un palier (>100K tokens, cache compris); les autres: 1M au tarif standard.
Batch -50%. Multiplicateur 1.1x si inference_geo US-only. Fast mode Opus 5.5: 8/40 (skill).
FX: 1 EUR = 1.1234 USD (BCE 2026-10-21, via WebSearch) => 1 USD = 0.89015 EUR. Taux du 22/10 non publie/non obtenu; pages BCE/frankfurter injoignables (proxy 403/DNS).
