# NLUniverseSMP 1.0.0 — broncode / testversie

**Status: niet gecompileerd of op een Paper-server getest. Niet op productie installeren zonder eerst op een kopie te testen.**

## Bouw via GitHub Actions
1. Maak een nieuwe GitHub-repository, upload de **inhoud** van deze map naar de root (dus `pom.xml` in de root).
2. Ga naar Actions → Build NLUniverseSMP → Run workflow.
3. Download artifact `NLUniverseSMP-jar` en pak de jar uit.
4. Stop je testserver, maak een volledige off-server backup, upload jar naar `plugins/`, start opnieuw.
5. Ga in de HUB staan en voer `/nlsmp sethub` uit. Test `/nlsmp join`, `/nlsmp leave`, `/nlsmp season`, `/nlsmp top`.

## Veiligheid en belangrijke beperkingen
- **Gebruik een testserver.** De wipe verwijdert na 14 dagen automatisch `smp`, `smp_nether`, `smp_the_end`. Deze namen staan in `config.yml`. Een backup-ZIP wordt eerst gemaakt in `plugins/NLUniverseSMP/smp-season-backups/`. Maak daarnaast een eigen off-server backup. Als er iets misgaat tijdens een gedeeltelijke wipe moet je handmatig herstellen.
- **Bestaande `smp`-werelden worden hergebruikt.** De plugin maakt ze alleen als ze nog niet bestaan. Gebruik geen productie-SMP zonder voorafgaand overleg over bestaande wereldbestanden.
- De wereldnamen `hub`, `world`, `world_nether`, `world_the_end` zijn uitgesloten als wipe-doel. Andere werelden die je zelf in de config invult kunnen wél gewist worden; wijzig die namen niet zonder backup.
- **Multiverse-Inventories / andere inventoryplugins:** schakel overlappende inventarisgroepen voor deze werelden uit. Deze plugin heeft eigen SMP/outside-inventarisscheiding en kan anders conflicteren.
- **Nether/End-portals:** basale omleiding aanwezig, maar controleer je Multiverse-portals en test terugreis en schaalcoördinaten zorgvuldig.
- **Totem-/enderpearllimiet:** controleert spelerinventory + enderchest bij oppakken en inventory-acties. Geen volledige preventie tegen alle containers, hoppers, shulkers of plugincommando's. Ook supply drops kunnen loot bevatten boven de limiet totdat spelers die proberen op te pakken.
- **Supply drops:** spawnen alleen in **reeds geladen chunks** en op geschikte vaste grond. Geen chunk-force-loading, om lag te voorkomen. Godtier-kans 1% **per succesvolle drop**. Drops kunnen vervallen na 45 minuten, ook met nog loot erin.
- **Spelerlocaties:** opgeslagen in `state.yml` op periodieke ticks en bij verlaten; de wereld moet bestaan bij het laden.
- **PvP:** actief in de SMP-werelden. De plugin maakt geen aparte PvP-arena's.
- **Hardcore death:** inventarisitems worden niet gedropt. De speler respawnt in HUB; de cooldown is 24 uur. De standaard hardcore-modus van Minecraft hoeft niet ingeschakeld te zijn.
- **Wipe reset:** SMP-werelden, SMP-inventories, SMP-enderchests, cooldowns en statistieken. De buiten-SMP-inventories worden bewaard.
- **Season reset:** eerste installatie start een nieuw seizoen van 14 dagen. Na herstart loopt dezelfde eindtijd door.
- **Performance:** wereldgeneratie en bulk-reset kunnen belastend zijn. Plan seizoenswissels op een rustig tijdstip en test op een kopie.

## SMP JOIN via pressure plate
- Plaats een **Impulse / Unconditional / Needs Redstone** command block onder een **massief vloerblok**, met de pressure plate erboven. Het redstonesignaal van de drukplaat kan door het massieve blok naar het command block lopen. Test de redstone-activatie op jouw server.
- Zet in het command block (zonder slash): `nlsmp joinplate`.
- Het commando werkt **alleen vanuit een command block** en kiest een speler die daadwerkelijk boven het command block staat; de hardcore-cooldown wordt gecontroleerd via dezelfde `enter()`-functie als `/nlsmp join`.
- Zorg dat `enable-command-block=true` staat in `server.properties`.
- **Let op:** dit is broncode voor een nieuwe build. Bouw de nieuwe jar via GitHub Actions; de bestaande jar krijgt de functie niet vanzelf.

## Commando's
- `/nlsmp join` — naar SMP / laatste locatie
- `nlsmp joinplate` — vanuit command block onder drukplaat
- `/nlsmp leave` — terug naar HUB
- `/nlsmp season` — afteller
- `/nlsmp top` — leaderboard
- `/nlsmp status` — beheerstatus
- `/nlsmp sethub` — admin: hubspawn opslaan
- `/nlsmp drop` — admin: drop proberen
- `/nlsmp cooldown <naam> clear` — admin: cooldown verwijderen
- `/nlsmp wipe confirm` — admin: **onmiddellijke onomkeerbare seizoensreset** (backup vooraf)

## Config
Zie `src/main/resources/config.yml` voor worldborder, cooldown, wipe, drops en limieten.

## Aanbevolen verbeteringen vóór livegang
Een geautomatiseerde integratietest met Paper, betrouwbare opslag voor inventories/locations tijdens crashes, een veilige twee-fasen wipe met herstel bij gedeeltelijk falen, volledige container-/shulkerlimieten, portals in alle richtingen, configureerbare seizoensstart op een rustig tijdstip, en betere chunk-safe supply-drop selectie.
