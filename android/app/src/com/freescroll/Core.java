package com.freescroll;

/** Every method is implemented in Rust (core/src/lib.rs). */
public final class Core {
    static { System.loadLibrary("freescroll_core"); }
    private Core() {}

    public static final int NONE = 0, GATE = 1, STOP = 2, BREAK = 3;

    public static native void setConfig(int scrollLimitScreens, int gateSecs, int gatePassMin, int breakMin);
    public static native boolean isSocialPackage(String pkg);
    public static native String urlIdForBrowser(String pkg);
    public static native String siteForUrl(String urlText);
    public static native int onForeground(String site, long nowMs);
    public static native int onScroll(String site, double px, double screenH, long nowMs);
    public static native void gatePassed(String site, long nowMs);
    public static native void startBreak(String site, long nowMs);
    public static native void continueAnyway(String site, double screenH);
    public static native long breakLeftSecs(String site, long nowMs);
    public static native void buzzSilenced();
    public static native String snapshot();
    public static native void restore(String snap);
    public static native String statsJson();
}
