# DuckHunt

A duck-hunting minigame plugin for Paper 26.2. Each "duck" is a
low-health mob — randomly picked from a configurable pool of types, each
with its own randomly-rolled movement speed — that can patrol a chain of
waypoints using real pathfinding AI. Faster ducks are worth more
leaderboard points, and ducks never collide with each other so quicker
ones can freely overtake slower ones. A duck never drops anything and,
if enabled, is instantly replaced when caught.

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

`/dh` works as a shorthand alias for `/duckhunt`. Two permissions gate
everything:

- **`duckhunt.user`** (defaults to **true**, everyone) — `/duckhunt top`.
- **`duckhunt.admin`** (defaults to **op**) — everything under
  `/duckhunt admin`.

| Command | Description |
|---|---|
| `/duckhunt top` | Shows the duck-kill leaderboard (see [Leaderboard](#leaderboard) below). |
| `/duckhunt admin spawner create <id> [amount]` | Saves a duck spawn point at your current location. `amount` is optional and overrides the default capacity for just this point. Re-running it on an existing id only updates its location/amount; its waypoint path is kept. |
| `/duckhunt admin spawner <id> max <amount>` | Changes how many ducks an existing spawn point keeps alive at once. |
| `/duckhunt admin spawner remove <id>` | Removes a spawn point (and its path). |
| `/duckhunt admin spawner list` | Lists configured spawn points, their capacity, how many ducks are currently alive at each, and how many waypoints they have. |
| `/duckhunt admin spawn <id\|all>` | Tops up one spawn point (or every spawn point) to its configured capacity. |
| `/duckhunt admin clear` | Removes every active duck from the world. |
| `/duckhunt admin start` | Starts automatic periodic spawning. |
| `/duckhunt admin stop` | Stops automatic periodic spawning. |
| `/duckhunt admin reload` | Reloads `config.yml`, `spawnpoints.yml`, `leaderboard.yml` and the `lang/*.yml` files. |
| `/duckhunt admin spawner <id> path add` | Appends your current location as the next waypoint in that spawn point's patrol path. |
| `/duckhunt admin spawner <id> path list` | Lists a spawn point's waypoints in order. |
| `/duckhunt admin spawner <id> path remove <index>` | Removes a single waypoint (1-based index, as shown by `path list`). |
| `/duckhunt admin spawner <id> path clear` | Clears every waypoint from a spawn point's path. |
| `/duckhunt admin spawner <id> path mode <loop\|pingpong\|stop>` | Overrides what the duck does after reaching the last waypoint. |
| `/duckhunt admin top reset <player\|all>` | Resets one player's (or everyone's) leaderboard tally. |
| `/duckhunt admin settings broadcast global` | Kill broadcasts go to every online player. |
| `/duckhunt admin settings broadcast radius <blocks>` | Kill broadcasts only reach players within `<blocks>` of the kill. |

## Waypoint paths

A spawn point's duck can either stand still or patrol a chain of
waypoints:

1. Create the spawn point: `/duckhunt admin spawner create duck1`.
2. Walk to each point you want the duck to pass through, in order, and
   run `/duckhunt admin spawner duck1 path add` at each one.
3. Make sure `duck.ai-enabled: true` in `config.yml` (see below) —
   without it, ducks have no AI at all and simply stand still.
4. `/duckhunt admin spawn duck1` (or auto-spawn) and the duck will walk
   from its spawn point through every waypoint you added, using real
   pathfinding (it climbs stairs, goes around obstacles, etc.).

What happens after the last waypoint depends on the path mode
(`spawn.default-path-mode` in `config.yml`, overridable per point with
`/duckhunt admin spawner <id> path mode <mode>`):

- **`loop`** — goes back to the first waypoint (its spawn point) and starts over.
- **`pingpong`** — walks the path backwards until it reaches the start, then forwards again.
- **`stop`** — stays put once it reaches the last waypoint.

A spawn point with no waypoints added just keeps its duck standing at
the spawn location, same as before.

## Random duck types & speed

Every time a duck spawns, its mob type is picked at random from
`duck.types` in `config.yml` (default: `ZOMBIE`, `HUSK`, `SKELETON`,
`SPIDER`, `DROWNED`), and its movement speed is randomly rolled between
`duck.speed.min` and `duck.speed.max`. That rolled speed sticks with the
duck for its whole life (survives `/duckhunt admin reload` and server
restarts) and is what the pathfinder uses to walk its route.

- Any Bukkit `EntityType` that maps to a mob works — invalid or
  non-mob entries in `duck.types` are skipped with a warning.
- Ducks always have collisions with other entities disabled, so a fast
  duck can freely pass through a slower one instead of pushing into it.
- Faster ducks are worth more leaderboard points — see below.

## Configuration

Settings live in three separate files inside the plugin's data folder:

- **`config.yml`** — duck stats (health, AI, speed, etc.), the
  server-wide default duck capacity (`spawn.default-amount`), whether a
  duck instantly respawns the moment it dies (`spawn.instant-respawn`),
  the default path mode (`spawn.default-path-mode`), how often the path
  task checks each duck's progress (`spawn.path-check-interval-ticks`),
  the automatic-spawning interval, and the leaderboard/kill-broadcast
  settings described below.
- **`spawnpoints.yml`** — the spawn point locations and waypoint paths
  themselves. Normally managed with `/duckhunt admin spawner
  create|<id> max|remove|<id> path ...` rather than edited by hand.
- **`leaderboard.yml`** — each player's kill tally. Normally managed
  with `/duckhunt top` / `/duckhunt admin top reset` rather than edited
  by hand.

### Duck count per spawn point

Every spawn point keeps a certain number of ducks alive at once, all
patrolling the same path:

- If a spawn point doesn't set its own `amount`, it uses
  `spawn.default-amount` from `config.yml`.
- To give one spawn point a different capacity, either pass it when
  creating the point (`/duckhunt admin spawner create duck1 3`) or
  change it later with `/duckhunt admin spawner duck1 max 3`.
- `/duckhunt admin spawn <id|all>` and the automatic-spawning task both
  just top a spawn point up to its capacity — they never overshoot it.

### Instant respawn

With `spawn.instant-respawn: true`, the moment a duck dies its spawn
point immediately spawns a replacement, instead of waiting for the next
`/duckhunt admin spawn` or auto-spawn cycle. This is independent of the
capacity setting: a spawn point with `amount: 3` and instant respawn
enabled always keeps 3 ducks up, refilled one at a time as they're
caught.

## Combo streaks

Chaining duck kills close together builds a combo streak (tracked
in-memory per player, not persisted across restarts):

- Killing a duck within `combo.window-seconds` (default `5.0`) of your
  last one extends your streak by 1; going quiet for longer resets it to
  zero. **Any** duck kill counts towards the streak — it doesn't need to
  qualify under `leaderboard.min-kill-distance` like leaderboard points
  do.
- Reaching one of `combo.tiers` in `config.yml` multiplies the points
  your *qualifying* kills are worth by that tier's `points-multiplier`,
  and — only if that tier sets a `particle` — gives every arrow you
  shoot from then on a trailing particle effect (until your streak drops
  below that tier, or climbs into a higher one). Set a tier's
  `particle.particle` to `none` (as the bundled `x5` tier does) to grant
  its points bonus with no visual trail at all — handy for a quiet entry
  tier that only "unlocks" the trail at a flashier one further up.
- Every kill that advances a streak plays `combo.sound.sound` (default a
  custom key, `block.trial_spawner.spawn_item`) to that player only,
  with its pitch climbing by `combo.sound.pitch-step` each kill and
  wrapping back down to 1.0 once it'd reach 2.0. Losing a streak instead
  plays a separate, fixed-pitch `combo.sound.lost-sound`.
- How the current count is shown is controlled by `combo.display.mode`:
  `chat`, `actionbar` (default), `title` (an invisible main line with the
  count as the subtitle), or `floating-text` (drifts up and shrinks away
  right in front of you, personal-only, landing with a small random
  offset each time so a fast streak's pop-ups don't perfectly stack).
  Reaching a new tier always sends a chat message on top of whichever
  mode is active, and a broken streak is always announced in chat too —
  plus, if `floating-text` is the active mode, one more pop-up for the
  loss itself.

Example default tiers: `5` kills → 1.2x points (no trail), `10` kills →
1.5x points plus a flame trail, `20` kills → 2x points plus an end rod
trail. Add, remove, or restyle tiers freely — each one's `particle` is
optional and falls back to `combo.default-particle` if omitted.

## Leaderboard

`/duckhunt top` shows the `leaderboard.top-size` (default 10) players
with the most points, stored in `leaderboard.yml`. Ranking is by total
points, with kill count as a tiebreaker.

- **Faster ducks are worth more points.** Each qualifying kill awards
  points linearly interpolated between `leaderboard.min-points` (for a
  duck rolled at `duck.speed.min`) and `leaderboard.max-points` (for one
  rolled at `duck.speed.max`) — see [Random duck types &
  speed](#random-duck-types--speed) above. That base value is then
  multiplied by the duck's own bonus if it was rare, and by your active
  [combo tier](#combo-streaks) if you're on a streak.
- **Only ranged/skillful kills count.** A kill only counts towards the
  leaderboard if the killer was at least `leaderboard.min-kill-distance`
  blocks (default `10.0`) away from the duck at the moment of death —
  standing next to it and hitting it doesn't add to your tally.
- **Kill broadcasts are separate from the leaderboard.** Whether (and to
  whom) a kill message is announced is controlled independently by
  `kill-broadcast` in `config.yml`:
  - `kill-broadcast.enabled` — turns the broadcast on/off entirely.
  - `kill-broadcast.mode: global` — sent to every online player.
  - `kill-broadcast.mode: radius` with `kill-broadcast.radius` (blocks)
    — only sent to players within that distance of the kill.
  - Change the mode in-game with `/duckhunt admin settings broadcast
    global` or `/duckhunt admin settings broadcast radius <blocks>`
    (persisted to `config.yml` immediately).
- Reset tallies with `/duckhunt admin top reset <player>` or
  `/duckhunt admin top reset all`.

## Translations

In-game messages live in `lang/en.yml` and `lang/es.yml` inside the
plugin's data folder (they're extracted there on first run). Each player
sees messages in their own client locale automatically; unrecognized
locales fall back to English. Add more `<locale>.yml` files (e.g.
`fr.yml`) to support additional languages, then `/duckhunt admin
reload`.

## How movement and cleanup work

Each duck is a single tagged `Mob` (its concrete type is whatever got
randomly picked from `duck.types`). If `duck.ai-enabled: true`, it's
spawned with `Bukkit.getMobGoals().removeAllGoals(...)` so none of its
default vanilla behaviour (attacking, wandering, looking around) remains,
and a repeating task drives it via `Mob.Pathfinder#moveTo(...)` — at its
own rolled speed — from waypoint to waypoint. If `duck.ai-enabled: false`,
`setAI(false)` is used instead and the duck simply stands still,
regardless of any configured path. Zombie-family mobs (`Zombie`, `Husk`,
`Drowned`, ...) also get `setShouldBurnInDay(false)` so they don't catch
fire; other mob families keep their vanilla daylight behaviour.

Ducks are spawned with `clearLootTable()` and the death listener clears
the event's drops/experience as a second safeguard, so killing a duck
never drops items or grants XP. Its spawn point's capacity is freed up
via a `PersistentDataContainer` tag read on death; the same mechanism
stores each duck's rolled speed, which is what determines its point
value (see [Leaderboard](#leaderboard) above).
