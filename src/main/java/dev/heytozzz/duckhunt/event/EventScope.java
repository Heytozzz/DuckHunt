package dev.heytozzz.duckhunt.event;

import dev.heytozzz.duckhunt.config.BroadcastMode;

import java.util.List;

/**
 * Who receives one of an event's announcement channels (its start
 * announcement, its countdown timer/milestones, or its winner
 * announcement): every online player, only players within a radius of
 * the event's spawn point, or only players in one of a list of worlds.
 */
public record EventScope(BroadcastMode mode, double radius, List<String> worlds) {

    public EventScope {
        worlds = (worlds == null) ? List.of() : List.copyOf(worlds);
    }
}
