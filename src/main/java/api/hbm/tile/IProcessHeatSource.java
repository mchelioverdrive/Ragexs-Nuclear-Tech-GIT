package api.hbm.tile;

/** Calibrated non-nuclear process heat: existing TU = 0.5 J, with a declared temperature ceiling. */
public interface IProcessHeatSource extends IHeatSource {
    int getProcessTemperatureC();
}
