# DuckHunt

A duck-hunting minigame plugin for Paper 26.2. Each "duck" is a low-health
zombie riding an invisible armor stand mounted on a minecart. When the
zombie dies, the plugin removes the armor stand and the minecart along
with it.

## Requirements

- Paper 26.2 (or a build based on it)
- Java 25

## Building

```bash
mvn clean package
```

The compiled jar will be at `target/DuckHunt-1.0.0-SNAPSHOT.jar`. Every
push/PR to `main` also builds automatically via GitHub Actions
(`.github/workflows/build.yml`), and the jar is uploaded as a downloadable
artifact on the corresponding Actions run.

## Commands

All subcommands require the `duckhunt.admin` permission (defaults to
server operators).

| Command | Description |
|---|---|
| `/duckhunt setspawn <id>` | Saves a duck spawn point at your current location. |
| `/duckhunt removespawn <id>` | Removes a spawn point. |
| `/duckhunt list` | Lists configured spawn points. |
| `/duckhunt spawn <id\|all>` | Spawns a duck at one spawn point, or fills every empty one. |
| `/duckhunt clear` | Removes every active duck (and leftover parts) from the world. |
| `/duckhunt start` | Starts automatic periodic spawning. |
| `/duckhunt stop` | Stops automatic periodic spawning. |
| `/duckhunt reload` | Reloads `config.yml` and the `lang/*.yml` files. |

`/dh` works as a shorthand alias for `/duckhunt`.

## Configuration

See `config.yml` for spawn points, duck stats (health, AI, speed, etc.)
and the automatic-spawning interval. Spawn points are usually added with
`/duckhunt setspawn <id>` rather than edited by hand.

## Translations

In-game messages live in `lang/en.yml` and `lang/es.yml` inside the
plugin's data folder (they're extracted there on first run). Each player
sees messages in their own client locale automatically; unrecognized
locales fall back to English. Add more `<locale>.yml` files (e.g.
`fr.yml`) to support additional languages, then `/duckhunt reload`.

## How the cleanup works

Each duck is three linked entities: `Zombie -> ArmorStand -> Minecart`
(the zombie rides the stand, which rides the cart). On `EntityDeathEvent`
for a tagged zombie, the plugin walks `getVehicle()` up the chain and
removes the armor stand and the minecart. A tagging fallback (a shared
group UUID stored via `PersistentDataContainer`) also cleans up any
leftover part in case the vehicle chain was already broken for some
reason.
