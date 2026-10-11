package com.lacuna.mod.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single player's scent trail (spec M9): positions recorded every {@code interval} ticks, kept for {@code window}
 * ticks, at most {@code maxPoints}. Pure Java, unit-tested; owned by the volatile ScentTracker.
 */
public final class ScentTrail {
    public record Point(double x, double y, double z, long time) {
        public double distanceSq(double px, double py, double pz) {
            double dx = x - px;
            double dy = y - py;
            double dz = z - pz;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private final int interval;
    private final long window;
    private final int maxPoints;
    private final List<Point> points = new ArrayList<>();

    public ScentTrail(int interval, long window, int maxPoints) {
        this.interval = Math.max(1, interval);
        this.window = Math.max(1, window);
        this.maxPoints = Math.max(1, maxPoints);
    }

    /** Records a point if at least {@code interval} ticks passed since the newest one. Returns true if recorded. */
    public boolean record(double x, double y, double z, long now) {
        if (!points.isEmpty() && now - points.get(points.size() - 1).time() < interval) {
            return false;
        }
        points.add(new Point(x, y, z, now));
        prune(now);
        return true;
    }

    public void prune(long now) {
        points.removeIf(p -> now - p.time() > window);
        while (points.size() > maxPoints) {
            points.remove(0);
        }
    }

    public List<Point> points() {
        return Collections.unmodifiableList(points);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }

    /** Index of the oldest point within {@code radius} of the given position, or -1. Following goes old -> new. */
    public int firstPointNear(double x, double y, double z, double radius) {
        double r2 = radius * radius;
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).distanceSq(x, y, z) <= r2) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the newest point within {@code radius} of the given position, or -1. */
    public int lastPointNear(double x, double y, double z, double radius) {
        double r2 = radius * radius;
        for (int i = points.size() - 1; i >= 0; i--) {
            if (points.get(i).distanceSq(x, y, z) <= r2) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the oldest point recorded strictly after {@code time}, or -1 (used to follow a trail by time). */
    public int firstAfter(long time) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).time() > time) {
                return i;
            }
        }
        return -1;
    }

    /** Breaks the trail at a point (e.g. the player hid in water): that point and everything older is forgotten. */
    public void breakAt(int index) {
        if (index < 0) {
            return;
        }
        int end = Math.min(index + 1, points.size());
        points.subList(0, end).clear();
    }
}
