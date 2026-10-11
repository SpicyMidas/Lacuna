package com.lacuna.mod.client.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** A named, bounded collection of outline boxes (blocks) and tracked entities, with an optional expiry time. */
public final class OutlineSet {
    public record Box(AABB box, int color) {}

    public record TrackedEntity(Entity entity, int color) {}

    private final int maxBoxes;
    private final int maxEntities;
    private final List<Box> boxes = new ArrayList<>();
    private final List<TrackedEntity> entities = new ArrayList<>();
    private long expiresAt = Long.MAX_VALUE;

    public OutlineSet(int maxBoxes, int maxEntities) {
        this.maxBoxes = Math.max(0, maxBoxes);
        this.maxEntities = Math.max(0, maxEntities);
    }

    public boolean addBox(AABB box, int color) {
        if (boxes.size() >= maxBoxes) {
            return false;
        }
        boxes.add(new Box(box, color));
        return true;
    }

    public boolean addEntity(Entity entity, int color) {
        if (entities.size() >= maxEntities) {
            return false;
        }
        entities.add(new TrackedEntity(entity, color));
        return true;
    }

    public boolean isFull() {
        return boxes.size() >= maxBoxes;
    }

    public List<Box> boxes() {
        return Collections.unmodifiableList(boxes);
    }

    public List<TrackedEntity> entities() {
        return Collections.unmodifiableList(entities);
    }

    public OutlineSet expiresAt(long gameTime) {
        this.expiresAt = gameTime;
        return this;
    }

    public boolean expired(long gameTime) {
        return gameTime >= expiresAt;
    }

    public boolean isEmpty() {
        return boxes.isEmpty() && entities.isEmpty();
    }
}
