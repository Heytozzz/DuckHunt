package dev.heytozzz.duckhunt.spawn;

/**
 * A single point along a duck's patrol path, resolved against its spawn
 * point's world. Added via "/duckhunt add path <id>".
 */
public record Waypoint(double x, double y, double z) {
}
