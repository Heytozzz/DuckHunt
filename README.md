# DuckHunt

A duck-hunting minigame plugin for Paper 26.2. Each "duck" is a low-health
zombie that can patrol a chain of waypoints using real pathfinding AI. The
duck never drops anything and, if enabled, is instantly replaced when
caught.

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
| `/duckhunt setspawn <id> [amount]` | Saves a duck spawn point at your current location. `amount` is optional and overrides the default capacity for just this point. Re-running it on an existing id only updates its location/amount; its waypoint path is kept. |
| `/duckhunt setamount <id> <amount>` | Changes how many ducks an existing spawn point keeps alive at once. |
| `/duckhunt removespawn <id>` | Removes a spawn point (and its path). |
| `/duckhunt list` | Lists configured spawn points, their capacity, how many ducks are currently alive at each, and how many waypoints they have. |
| `/duckhunt spawn <id\|all>` | Tops up one spawn point (or every spawn point) to its configured capacity. |
| `/duckhunt clear` | Removes every active duck from the world. |
| `/duckhunt start` | Starts automatic periodic spawning. |
| `/duckhunt stop` | Stops automatic periodic spawning. |
| `/duckhunt reload` | Reloads `config.yml`, `spawnpoints.yml` and the `lang/*.yml` files. |
| `/duckhunt add path <id>` | Appends your current location as the next waypoint in that spawn point's patrol path. |
| `/duckhunt path list <id>` | Lists a spawn point's waypoints in order. |
| `/duckhunt path remove <id> <index>` | Removes a single waypoint (1-based index, as shown by `path list`). |
| `/duckhunt path clear <id>` | Clears every waypoint from a spawn point's path. |
| `/duckhunt path mode <id> <loop\|pingpong\|stop>` | Overrides what the duck does after reaching the last waypoint. |

## Waypoint paths

A spawn point's duck can either stand still or patrol a chain of
waypoints:

1. Create the spawn point: `/duckhunt setspawn duck1`.
2. Walk to each point you want the duck to pass through, in order, and
   run `/duckhunt add path duck1` at each one.
3. Make sure `duck.ai-enabled: true` in `config.yml` (see below) —
   without it, ducks have no AI at all and simply stand still.
4. `/duckhunt spawn duck1` (or auto-spawn) and the duck will walk from
   its spawn point through every waypoint you added, using real
   pathfinding (it climbs stairs, goes around obstacles, etc.).

What happens after the last waypoint depends on the path mode
(`spawn.default-path-mode` in `config.yml`, overridable per point with
`/duckhunt path mode <id> <mode>`):

- **`loop`** — goes back to the first waypoint (its spawn point) and starts over.
- **`pingpong`** — walks the path backwards until it reaches the start, then forwards again.
- **`stop`** — stays put once it reaches the last waypoint.

A spawn point with no waypoints added just keeps its duck standing at
the spawn location, same as before.

## Configuration

Settings live in two separate files inside the plugin's data folder:

- **`config.yml`** — duck stats (health, AI, speed, etc.), the
  server-wide default duck capacity (`spawn.default-amount`), whether a
  duck instantly respawns the moment it dies (`spawn.instant-respawn`),
  the default path mode (`spawn.default-path-mode`), how often the path
  task checks each duck's progress (`spawn.path-check-interval-ticks`),
  and the automatic-spawning interval.
- **`spawnpoints.yml`** — the spawn point locations and waypoint paths
  themselves. Normally managed with `/duckhunt setspawn` / `setamount` /
  `removespawn` / `add path` / `path ...` rather than edited by hand.

### Duck count per spawn point

Every spawn point keeps a certain number of ducks alive at once, all
patrolling the same path:

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

## How movement and cleanup work

Each duck is a single tagged `Zombie`. If `duck.ai-enabled: true`, it's
spawned with `Bukkit.getMobGoals().removeAllGoals(...)` so none of its
default zombie behaviour (attacking, wandering, looking around) remains,
and a repeating task drives it via `Mob.Pathfinder#moveTo(...)` from
waypoint to waypoint. If `duck.ai-enabled: false`, `setAI(false)` is used
instead and the duck simply stands still, regardless of any configured
path.

Ducks are spawned with `clearLootTable()` and the death listener clears
the event's drops/experience as a second safeguard, so killing a duck
never drops items or grants XP. Its spawn point's capacity is freed up
via a `PersistentDataContainer` tag read on death.
