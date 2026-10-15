package com.hbm.realsim;

/** Native gas-turbine control schedule; each startup step still requires paid fuel/lubricant. */
public final class GasTurbineCycle {
    public static final int STARTUP_TICKS = 580;
    public static final int LUBE_MICROLITERS_PER_TICK = 50;
    private GasTurbineCycle() { }
    public static int startupRpm(int counter, int idle) {
        if(counter <= 20) return 5 * counter;
        if(counter <= 40) return 100 - 5 * (counter - 20);
        return counter <= 50 ? 0 : idle * Math.min(530, counter - 50) / 530;
    }
    public static int startupTemperature(int counter, int idle) { return counter <= 50 ? 0 : idle * Math.min(530, counter - 50) / 530; }
    public static int targetRpm(int slider, int throttle, int idle) { return slider == 0 ? idle : idle + Math.max(25, throttle) * 90 / 100; }
    public static int load(int slider, int throttle, int rpm, int idle) { return slider == 0 ? 0 : Math.min(Math.max(25, throttle), Math.max(0, (rpm - idle) * 100 / 90)); }
}
