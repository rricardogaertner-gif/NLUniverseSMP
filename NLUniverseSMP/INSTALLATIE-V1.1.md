# NLUniverseSMP v1.1 — Installatie

1. Maak eerst een back-up in Nova. Bouw de JAR met GitHub Actions (Java 21).
2. Stop de server, upload de nieuwe JAR naar `/plugins/`, start opnieuw.
3. Ga in de Fantasy HUB op de juiste spawnlocatie staan en gebruik `/nlsmp sethub`. De standaard wereld is `nluniverse_hub`, maar de coördinaten moet je zelf instellen.
4. Test supplydrops met `/nlsmp drop`. De plugin laadt nu chunks asynchroon, ook als er geen spelers in de buurt zijn.
5. Hardcore-cooldown blijft 24 uur.
6. `automatic-season-wipe: false` voorkomt dat werelden na 14 dagen automatisch gewist worden. `/nlsmp wipe confirm` blijft destructief; niet gebruiken zonder back-up.
7. De plugin scheidt alleen SMP-inventaris van buiten-SMP. PvP/Vanilla/HUB vereisen later een centraal inventarissysteem.

Dit is een broncodepatch. Controleer de GitHub Actions-build en test met een back-up voordat spelers toegang krijgen.
