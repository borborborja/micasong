# MiCaSong

Reproductor de música i audiollibres per a **Android + Android Auto**, construït com a clon
funcional de *Symfonium* seguint l'especificació mestra
(`instruccions/symfonium-clon-especificacio-mestra.md`).

> ⚖️ **Marca i codi propis.** *Symfonium* és marca de tercers. MiCaSong reimplementa les
> **funcions** (que no tenen copyright) amb codi, nom, icona i textos originals. No conté cap
> recurs gràfic ni codi del producte original.

Filosofia: **offline-first**, **agregació multi-proveïdor** i **personalització**, tal com marca
la bíblia (§1).

---

## Estat actual

L'app **compila** (`assembleDebug`), **passa lint** sense errors (incloent-hi la validació
d'apps de mitjans d'Android Auto) i té una **suite de 246 tests unitaris** que verifica ~36 motors de lògica pura (smart filters,
smart playlists, mescles personals, string templates, LRC, ReplayGain, Smart Queue/Flow, corbes
de fundit, cues múltiples, tags, Artist NFO, cache offline, auth Subsonic, M3U/PLS, sync diferencial,
AutoEQ, backup, waveform/smart-fades, marcatge de reproducció, sleep timer, capítols, camps Now Playing,
transcodificació, renderers/cast, weighted shuffle, accions media session, ràdio internet, auth Jellyfin,
sync de servidor amb preservació d'estat, model d'equalitzador): `./gradlew :app:testDebugUnitTest` → **217 passing, 0 failures**.

### Implementat (Fase 1 — MVP local + fonaments d'ecosistema)

| Àrea | Spec | Estat |
|------|------|-------|
| Arquitectura per capes (providers / sync / db / playback / ui) | §2 | ✅ |
| Proveïdor local (MediaStore) amb escaneig a BD | §5.3, §48 | ✅ |
| Interfície comuna `MediaProvider` + **matriu de capacitats** | §2, §6 | ✅ |
| Proveïdor **Subsonic/OpenSubsonic** (auth token MD5+salt verificat vs vector oficial, endpoints, sync HTTP) | §5.1, §47 | ✅ + tests |
| Proveïdor **Jellyfin** (auth header MediaBrowser, endpoints Items/Audio, sync HTTP) | §46 | ✅ + tests |
| **Onboarding de servidors** (afegir/treure Subsonic/Jellyfin des d'Ajustos, persistit a Room) | §4-5 | ✅ |
| **Sync diferencial de servidor** (diff + preservació d'estat d'usuari: favorits/ratings/counts) | §9-10 | ✅ + tests |
| **Parser de tags custom** (multi-valor, separadors, MBIDs, dates, rating, MusicBee Love) | §7 | ✅ + tests |
| **Artist Information Folder** (parser `artist.nfo` Kodi: bio/gèneres/estils/moods/thumbs/MBID) | §8 | ✅ + tests |
| **Motor de cache offline** (permanent/rolling/rules, evicció LRU, reconciliació de regles) | §34 | ✅ + tests |
| **Parser de playlists M3U/PLS** (EXTINF, títol/durada, PLS ordenat per índex) | §32 | ✅ + tests |
| **Sync diferencial** (diff afegits/eliminats/canviats/sense-canvis per clau + comparador) | §9 | ✅ + tests |
| **Ràdios d'internet** (cua de totes en clicar, auth bàsica a la URL, entrada PLS aleatòria) | §10 | ✅ + tests |
| **Model de backup `.symfbkpz`** (contingut seleccionable, flags API, contrasenya obligatòria) | §43 | ✅ + tests |
| Base de dades unificada Room (tracks/albums/artists/genres/playlists + estat d'usuari) | §10 | ✅ |
| Navegació Inici / Biblioteca / Buscar / Ajustos + mini-player | §21 | ✅ |
| Home: graella de shortcuts, carrusels, mixes (pistes/àlbums) | §22.1, §17 | ✅ |
| Biblioteca amb pestanyes (Àlbums/Artistes/Pistes/Gèneres/Llistes) | §22.2, §29 | ✅ |
| Pàgines de detall Àlbum / Artista / Gènere / Playlist amb capçalera + barra d'accions | §22.3-6 | ✅ |
| Cerca en viu (pistes/àlbums/artistes) | §29 | ✅ |
| Motor Media3/ExoPlayer: **gapless**, media session, notificació, audio focus | §11, §18 | ✅ |
| Now Playing compacte (mini-player) + ampliat amb slider/controls | §24 | ✅ |
| Estat d'usuari: favorits, ratings, **play counts**, **resume points** | §10, §20 | ✅ |
| **Marcatge de reproducció** (llindars played/skip/resume + rollback audiollibre, connectat al listener) | §11, §20 | ✅ + tests |
| **Android Auto**: `MediaLibraryService` + arbre de navegació (Inicio/Recientes/Biblioteca/Favoritos) + cerca per veu | §38 | ✅ |
| **API broadcast** (Tasker): parser tipat de totes les accions + dispatch (control, sync, mescles, force_provider_connection) | §42 | ✅ + tests |
| Temes Material 3 + **Material You** + modes Clar/Fosc/Negre | §25 | ✅ |
| **Temes personalitzats JSON** (import/export, compat. Material Theme Builder, parser hex) | §25 | ✅ + tests |
| **App styles** (9 presets Modern/Floating/Universal/…, nav+llista+columnes, import/export) | §28 | ✅ + tests |
| **Profiles** (app style + Smart Queue + filtres globals; mode música/audiollibre; `load_profile`) | §28-bis | ✅ + tests |
| Ajustos persistits (DataStore) amb toggles reals + arbre representatiu | §44 | ✅ |

### Fase 3 — Intel·ligència (motors implementats + verificats)

| Component | Spec | Estat |
|-----------|------|-------|
| **Smart filters** (regles + grups niuables ALL/ANY + operadors, incl. Is present/missing) | §30 | ✅ + tests |
| **Smart playlists** (filtre + ordre + límit + stable random, JSON persistible) | §31 | ✅ + tests |
| **Mescles personals** (algorisme de buckets: exclou recents/1-2★, distribució artistes/àlbums) | §17 | ✅ + tests (integrat a "Mix de pistas") |
| **String templates** (%field%, blocs `{}`, `%lb%`, `^^CAPS^^`, labels) | §27 | ✅ + tests |
| **Camps de Now Playing** (Track+estat+capítols+timer → mapa de camps, end-to-end amb templates) | §27 | ✅ + tests |

### Fase 4 — Àudio avançat (en curs)

| Component | Spec | Estat |
|-----------|------|-------|
| **Equalitzador complet** (model per-sortida + presets 5/10/15/31 + preamp + bass/virtualizer, aplicat a AudioFx, **UI + persistència + wiring reactiu al servei**, només local) | §14 | ✅ + tests + UI |
| **ReplayGain** (tags REPLAYGAIN + R128, mode track/album/auto, clamp de peak) | §15 | ✅ + tests |
| **Lletres LRC** (parser sincronitzat, multi-timestamp, meta tags, línia activa) | §41 | ✅ + tests |
| **Smart Queue** (extensió per gènere/artista/aleatori) | §16 | ✅ + tests |
| **Smart Flow** (7 modes, insercions en temps real, flag de sonic analysis) | §16 | ✅ + tests |
| **AutoEQ** (parser GraphicEQ.txt + APO, interpolació log-freq, resample a bandes) | §14 | ✅ + tests |
| **Weighted shuffle** (greedy LRU per artista/àlbum, 0 adjacències en balancejat, connectat) | §17 | ✅ + tests |
| **Corbes de fundit** (Linear/Smooth/Bungee/Flat/Disabled, in/out mirall) | §13 | ✅ + tests |
| **Cues múltiples** (fins a 15 independents, estat propi, música+audiollibre) | §16 | ✅ + tests |
| **Smart fades / waveform** (detecció de silenci, punts òptims de fundit, skip de dead-air) | §13 | ✅ + tests |
| **Sleep timer** (compte enrere + mode fi-de-cançó `eos`, extend/cancel) | §27 | ✅ + tests |
| **Decisió de transcodificació** (budget mòbil/Wi-Fi, Chromecast→AAC, mapeig API INT_PARAMETER) | §11-12 | ✅ + tests |
| **Model de renderers/cast** (tipus API 0/1/3/6, SELECT_RENDERER, reset automàtic, DSP només local) | §36 | ✅ + tests |
| **Accions de media session** (Acció 1/2/3, seek ±10/30s, capítol ant./seg., botons auriculars) | §18,§37 | ✅ + tests |
| **Capítols d'audiollibre** (ID3v2, navegació, camps `chapter.*`/`next.chapter.*`/`chapter.all.*`) | §19 | ✅ + tests |
| Aplicació de crossfade a l'stream · transcode FFmpeg (FFmpegKit) · ReplayGain per-pista (cal TagLib) · lletres a la UI | §7,§13,§15 | ⬜ pendent (deps pesades) |

### Bastida / futur

- **Fase 2** ✅ — Subsonic/OpenSubsonic + Jellyfin, onboarding de servidors, sync diferencial amb preservació d'estat, image cache, cerca. *(Pendents: proveïdors Plex/Emby/Kodi/SMB/WebDAV/núvol.)*
- **Fase 4** ✅ (nucli) — equalitzador complet (model+UI+persistència+aplicació), AutoEQ, ReplayGain, corbes de fundit/smart fades, cues múltiples, Smart Queue/Flow, LRC, sleep timer, capítols, transcode-decision. *(Pendents amb deps pesades: PEQ/256 bandes, aplicació real de crossfade, transcode FFmpeg, ReplayGain per-pista via TagLib, lletres a la UI.)*
- **Fase 5** — Cast (Chromecast/UPnP/Sonos/Remote), widgets, Wear OS, backup/restore `.symfbkpz`.
- **Fase 6** — Motor audiòfil Hi-Res/bit-perfect, DSD, pipeline DSP 64-bit (§11-bis) — *requereix maquinari real (DACs/DAPs); opcional segons la bíblia*.

---

## Arquitectura

```
com.micasong.player
├─ data
│  ├─ db          → Room: entitats, DAOs, base de dades unificada
│  ├─ model       → models de domini (provider-agnòstics, camps nullable)
│  ├─ provider    → MediaProvider + ProviderCapabilities + LocalProvider + SubsonicProvider
│  ├─ repository  → MediaRepository (única font de veritat, orquestra proveïdors↔BD)
│  └─ settings    → SettingsRepository (DataStore)
├─ playback
│  ├─ PlaybackService        → MediaLibraryService (UI + notificació + Android Auto)
│  ├─ MediaTree              → arbre de navegació browsable per Auto/Wear
│  ├─ PlaybackConnection     → MediaController del costat UI (StateFlow)
│  └─ PlaybackStatsListener  → play counts / resume points
├─ api            → ApiReceiver (broadcast intents d'automatització)
└─ ui             → Compose (theme, navigation, home, library, album, artist,
                    genre, playlist, search, settings, nowplaying, components)
```

**Stack:** Kotlin · Jetpack Compose (Material 3) · Media3/ExoPlayer · Room · Hilt · Coil ·
DataStore · Navigation Compose · Coroutines/Flow.

---

## Distribucions (FOSS vs completa)

MiCaSong es compila en **dos flavors** (spec §45, compatibilitat F-Droid):

| Flavor | `applicationId` | Contingut | Ús |
|--------|-----------------|-----------|-----|
| **foss** | `com.micasong.player.foss` | 100% lliure, sense dependències propietàries | F-Droid, purament FOSS |
| **full** | `com.micasong.player` | inclou components propietaris (Google Cast) | Play Store / ús general |

`BuildConfig.IS_FOSS` distingeix les dues edicions en temps de compilació. Es poden instal·lar en
paral·lel (l'edició FOSS té el sufix `.foss`).

## Com compilar i executar

Requisits: JDK 17, Android SDK (platform 35, build-tools 35).

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # o la ruta del teu JDK 17
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"

# Compilar les dues APKs de debug
./gradlew :app:assembleFossDebug   # → app/build/outputs/apk/foss/debug/app-foss-debug.apk
./gradlew :app:assembleFullDebug   # → app/build/outputs/apk/full/debug/app-full-debug.apk

# Instal·lar en un dispositiu/emulador connectat
./gradlew :app:installFossDebug    # o installFullDebug
```

## Integració contínua (GitHub Actions)

El workflow `.github/workflows/build.yml` compila **totes dues APKs** (FOSS i completa) a cada push
i PR, executa els tests unitaris, i les publica com a *artifacts*. En fer push d'una etiqueta `v*`
(p. ex. `v0.1.0`) també crea una **release** de GitHub amb les dues APKs adjuntes.

En obrir l'app: concedeix el permís d'accés a l'àudio i prem **Sincronizar** — s'escaneja la
música del dispositiu i s'omple la biblioteca. Connecta el telèfon a Android Auto (o al DHU) i
MiCaSong apareixerà amb les pestanyes Inicio/Recientes/Biblioteca/Favoritos.

### Provar l'API broadcast (Tasker / adb)

```bash
adb shell am broadcast -a com.micasong.api.MEDIA_COMMAND --es COMMAND play
adb shell am broadcast -a com.micasong.api.MEDIA_COMMAND --es COMMAND next
adb shell am broadcast -a com.micasong.api.MEDIA_SYNC
```

---

## Nota d'implementació sobre el proveïdor Subsonic

`SubsonicProvider` implementa l'autenticació salted-token (`t=MD5(password+salt)`, `s=salt`) i la
construcció d'endpoints (`ping`, `getArtists`, `getAlbumList2`, `stream`…) tal com descriu §47. El
`sync()` fa peticions HTTP reals i és defensiu: si el servidor no és accessible, retorna un snapshot
buit i l'app segueix funcionant amb el que ja hi ha a la BD (offline-first). Per provar-lo cal
donar d'alta un servidor Navidrome/Subsonic o Jellyfin des d'**Ajustos → Proveedores de medios** (Fase 2, ja implementat).
