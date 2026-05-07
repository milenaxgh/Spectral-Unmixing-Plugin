package spectralunmixing;
import java.awt.Color;

public class SpectralEntry {

    public String   name;
    public double[] spectrum;
    public Color    color;
    public boolean  selected;

    public SpectralEntry(String name, double[] spectrum, Color color) {
        this.name     = name;
        this.spectrum = spectrum;
        this.color    = color;
        this.selected = true;
    }

    public double[] normalized() {
        double max = 0;
        for (double v : spectrum) if (v > max) max = v;
        if (max == 0) return spectrum.clone();
        double[] n = new double[spectrum.length];
        for (int i = 0; i < spectrum.length; i++) n[i] = spectrum[i] / max;
        return n;
    }

    @Override
    public String toString() { return name; }
}