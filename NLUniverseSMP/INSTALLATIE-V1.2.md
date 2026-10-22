# NLUniverseSMP v1.2 — Loot tiers

Supplydrops: Common 70%, Rare 25%, Legendary 5% (instelbaar).

Fix: live chest inventory vullen zonder Chest.update() na het vullen.

1. Maak een back-up van je SMP-werelden en plugin-data.
2. Upload projectbestanden naar GitHub, behoud .github/workflows/maven.yml.
3. Bouw met GitHub Actions.
4. Stop de server, vervang de oude JAR, start opnieuw.
5. Bestaande config.yml krijgt niet automatisch nieuwe sleutels; voeg toe onder supply-drops:

   loot:
     rare-chance: 0.25
     legendary-chance: 0.05

6. Test in SMP met /nlsmp drop en controleer de kist.

Let op: als er nog steeds lege kisten zijn, stuur de serverlogs en de locatie.
