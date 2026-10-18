package factoryscope.acceptance;

import arc.struct.Seq;

/** Shared queue, timing and result state for the real-client acceptance scenarios. */
final class AcceptanceContext{
    final Seq<String> failures = new Seq<>();
    final Seq<Runnable> actions = new Seq<>();
    int checks;
    int areaOriginX, areaOriginY;
    boolean areaOriginCaptured;
    float nextActionDelay = AcceptanceHarness.TICKS_BETWEEN_ACTIONS;

    void captureAreaOrigin(int x, int y){
        areaOriginX = x;
        areaOriginY = y;
        areaOriginCaptured = true;
    }

    int areaOriginX(int fallback){ return areaOriginCaptured ? areaOriginX : fallback; }
    int areaOriginY(int fallback){ return areaOriginCaptured ? areaOriginY : fallback; }
}
