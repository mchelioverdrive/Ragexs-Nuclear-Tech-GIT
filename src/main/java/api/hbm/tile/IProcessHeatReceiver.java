package api.hbm.tile;

/** Opt-in for a designed thermal contact. Implementations locate the source at their actual receiving port. */
public interface IProcessHeatReceiver {
    /** Only a directly contacting, upward-output source at an intended thermal receiver may be returned. */
    IProcessHeatSource getProcessHeatSource();
}
