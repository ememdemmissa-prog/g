# Goblin Mod

Forge 1.20.1 (47.x) + GeckoLib 4.8.4+. Adds the Goblin Thief.

## Features

- **Stealing.** A goblin hit steals an item from your inventory, then the goblin runs away for 20–40 seconds. It prefers treasure: rare, epic and enchanted items are far more likely to be taken.
- **Greed.** Goblins run to items lying on the ground within 12 blocks and pocket them.
- **Loot glint.** A goblin carrying stolen goods leaves golden sparkles, so you can track it down. It never despawns while carrying loot.
- **Payback.** Killing a goblin drops everything it stole, plus gold nuggets (affected by Looting) and sometimes an emerald.
- **Gangs.** Goblins spawn naturally in groups of 1–3 in forests, taigas, savannas, plains, meadows and swamps. Hit one, and its friends join the fight.

## Build

```sh
./gradlew build   # -> build/libs/goblinmod-<version>.jar
```

Requires JDK 17.
