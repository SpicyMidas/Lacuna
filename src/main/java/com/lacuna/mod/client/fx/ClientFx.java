package com.lacuna.mod.client.fx;

/**
 * A client-side visual effect owned by {@link FxManager}: ticked 20 times per second and rendered every frame
 * (camera-relative pose, after particles). Implementations must be cheap and must never touch server state.
 */
public interface ClientFx {
    /** Advances the effect by one game tick; returns false once it has finished and can be dropped. */
    boolean tick();

    /** Draws the effect; the context's pose stack is already camera-relative (subtract {@code ctx.cam()}). */
    void render(FxRenderContext ctx);
}
