# DuckHunt

A duck-hunting minigame plugin for Paper 26.2. Each "duck" is a low-health
zombie riding an invisible armor stand mounted on a minecart. When the
zombie dies, the plugin removes the armor stand and the minecart along
with it, and the duck never drops anything.

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
server operators). `/dh` works as a shorthand alias for `/duckhunt`.

| Command | Description |
|---|---|
| `/duckhunt setspawn <id> [amount]` | Saves a duck spawn point at your current location. `amount` is optional and overrides the default capacity for just this point. |
| `/duckhunt setamount <id> <amount>` | Changes how many ducks an existing spawn point keeps alive at once. |
| `/duckhunt removespawn <id>` | Removes a spawn point. |
| `/duckhunt list` | Lists configured spawn points, their capacity, and how many ducks are currently alive at each. |
| `/duckhunt spawn <id\|all>` | Tops up one spawn point (or every spawn point) to its configured capacity. |
| `/duckhunt clear` | Removes every active duck (and leftover parts) from the world. |
| `/duckhunt start` | Starts automatic periodic spawning. |
| `/duckhunt stop` | Stops automatic periodic spawning. |
| `/duckhunt reload` | Reloads `config.yml`, `spawnpoints.yml` and the `lang/*.yml` files. |

## Configuration

Settings live in two separate files inside the plugin's data folder:

- **`config.yml`** — duck stats (health, AI, speed, etc.), the
  server-wide default duck capacity (`spawn.default-amount`), whether a
  duck instantly respawns the moment it dies (`spawn.instant-respawn`),
  and the automatic-spawning interval.
- **`spawnpoints.yml`** — the spawn point locations themselves. Normally
  managed with `/duckhunt setspawn` / `setamount` / `removespawn` rather
  than edited by hand.

### Duck count per spawn point

Every spawn point keeps a certain number of ducks alive at once:

- If a spawn point doesn't set its own `amount`, it uses
  `spawn.default-amount` from `config.yml`.
- To give one spawn point a different capacity, either pass it when
  creating the point (`/duckhunt setspawn duck1 3`) or change it later
  with `/duckhunt setamount duck1 3`.
- `/duckhunt spawn <id|all>` and the automatic-spawning task both just
  top a spawn point up to its capacity — they never overshoot it.

### Instant respawn

With `spawn.instant-respawn: true`, the moment a duck dies its spawn
point immediately spawns a replacement, instead of waiting for the next
`/duckhunt spawn` or auto-spawn cycle. This is independent of the
capacity setting: a spawn point with `amount: 3` and instant respawn
enabled always keeps 3 ducks up, refilled one at a time as they're
caught.

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

Ducks are also spawned with `clearLootTable()` and the death listener
clears the event's drops/experience as a second safeguard, so killing a
duck never drops items or grants XP.
