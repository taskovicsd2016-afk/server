# HelloPlugin

Egyszerű Minecraft (Paper/Spigot) plugin, amely a játékosok játékidejét követi,
kitiltást kezel, és levédési parancsokat biztosít WorldGuard segítségével.

## Parancsok

- `/hello` – köszönő üzenet.
- `/online` – listázza az online játékosokat és a szerveren töltött idejüket.
- `/ora <játékosnév>` – megmutatja egy játékos összesített játékidejét és az utolsó belépés idejét.
- `/ban <játékosnév> [indok]` – végleges kitiltás indokkal.
- `/tempban <játékosnév> <idő> [indok]` – ideiglenes kitiltás indokkal.
- `/jatekos <játékosnév>` – megmutatja az ugyanarról az IP-ről játszott karaktereket és azok
  tiltási státuszát.
- `/leved` – levédi a környező területet a saját nevedre.
- `/torles` – törli a saját területet, ha azon állsz.
- `/setotthon` – otthont állít be a saját területeden.
- `/otthon` – teleportál a saját területed otthonára.
- `/otthon <név>` – teleportál annak a játékosnak a területére, akihez hozzá vagy adva.
- `/barat hozzaadas <név>` – hozzáad egy játékost a területedhez.
- `/barat` – listázza, mely területekhez vagy hozzáadva.

### Idő formátumok a /tempban parancshoz

- `m` = perc (példa: `30m`)
- `h` = óra (példa: `2h`)
- `d` = nap (példa: `7d`)
- `mo` = hónap (példa: `1mo`, 30 napnak számít)

### Levédés konfiguráció

A `plugins/HelloPlugin/config.yml` fájlban állítható:

- `region.radius` – a levédés sugara blokkokban.
- `region.max-per-player` – maximális levédések száma játékosonként.

## Build

```bash
gradle build
```

A jar fájl a `build/libs/hello-plugin-1.0.0.jar` alatt lesz.

## Telepítés

1. Telepítsd a WorldGuard plugint a szerverre.
2. Másold a jar fájlt a szerver `plugins/` mappájába.
3. Indítsd újra a szervert.
4. Használd a parancsokat a játékban.

## Adattárolás

A plugin a `plugins/HelloPlugin/data.yml` fájlban tárolja a játékidőt, az utolsó belépés
időpontját, a kitiltásokat, az IP-alapú összerendeléseket, és a területek otthon pontjait.
