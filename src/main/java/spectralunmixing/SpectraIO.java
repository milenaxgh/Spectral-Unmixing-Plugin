package spectralunmixing;

import ij.IJ;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.io.OpenDialog;
import ij.io.SaveDialog;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SpectraIO — Save and load spectral libraries as .csl files.
 *
 * .csl format (plain text, comma-separated):
 *   Line 1: #CSL v1 bands=28
 *   Per entry:
 *     NAME=<name>
 *     COLOR=<r>,<g>,<b>
 *     SPECTRUM=<v0>,<v1>,...,<vN>
 *   End:
 *     END
 */
public class SpectraIO {

    // Export

    /**
     * Saves the current library to a .csl file chosen by the user.
     */
    public static void exportLibrary(List<SpectralEntry> library, int bands) {
        if (library.isEmpty()) {
            IJ.error("Library is empty. Nothing to export.");
            return;
        }

        SaveDialog sd = new SaveDialog("Save Spectral Library", "library", ".csl");
        if (sd.getFileName() == null) return;
        String path = sd.getDirectory() + sd.getFileName();

        try (PrintWriter pw = new PrintWriter(new FileWriter(path))) {
            pw.println("#CSL v1 bands=" + bands);
            for (SpectralEntry e : library) {
                pw.println("NAME=" + e.name);
                pw.println("COLOR=" + e.color.getRed() + ","
                        + e.color.getGreen() + ","
                        + e.color.getBlue());
                StringBuilder sb = new StringBuilder("SPECTRUM=");
                for (int b = 0; b < e.spectrum.length; b++) {
                    if (b > 0) sb.append(",");
                    sb.append(String.format("%.6f", e.spectrum[b]));
                }
                pw.println(sb);
                pw.println("END");
            }
            IJ.log("Library exported: " + path
                    + "  (" + library.size() + " entries)");
            IJ.showMessage("Export complete",
                    "Saved " + library.size() + " spectra to:\n" + path);
        } catch (IOException ex) {
            IJ.error("Export failed: " + ex.getMessage());
        }
    }

    // Import

    /**
     * Loads spectra from a .csl file, shows a preview dialog,
     * and returns the entries the user chose to import.
     * Returns null if cancelled.
     */
    public static List<SpectralEntry> importFromLibrary(int currentBands) {
        OpenDialog od = new OpenDialog("Open Spectral Library (.csl)");
        if (od.getFileName() == null) return null;
        String path = od.getDirectory() + od.getFileName();

        // Parse the file
        List<SpectralEntry> loaded = parseCSL(path, currentBands);
        if (loaded == null || loaded.isEmpty()) return null;

        // Show preview + selection dialog
        return showImportDialog(loaded, currentBands);
    }

    // Parser

    private static List<SpectralEntry> parseCSL(String path, int currentBands) {
        List<SpectralEntry> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            String  name     = null;
            Color   color    = Color.RED;
            double[] spectrum = null;
            int fileBands = currentBands;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#CSL")) {
                    // Parse band count from header
                    if (line.contains("bands=")) {
                        try {
                            fileBands = Integer.parseInt(
                                    line.split("bands=")[1].trim());
                        } catch (Exception ignored) {}
                    }
                } else if (line.startsWith("NAME=")) {
                    name = line.substring(5);
                } else if (line.startsWith("COLOR=")) {
                    String[] rgb = line.substring(6).split(",");
                    color = new Color(Integer.parseInt(rgb[0].trim()),
                            Integer.parseInt(rgb[1].trim()),
                            Integer.parseInt(rgb[2].trim()));
                } else if (line.startsWith("SPECTRUM=")) {
                    String[] vals = line.substring(9).split(",");
                    spectrum = new double[vals.length];
                    for (int i = 0; i < vals.length; i++)
                        spectrum[i] = Double.parseDouble(vals[i].trim());
                } else if (line.equals("END")) {
                    if (name != null && spectrum != null) {
                        // Resample if band count differs
                        double[] s = resampleIfNeeded(spectrum, currentBands);
                        result.add(new SpectralEntry(name, s, color));
                    }
                    name = null; color = Color.RED; spectrum = null;
                }
            }
        } catch (IOException ex) {
            IJ.error("Could not read file: " + ex.getMessage());
            return null;
        } catch (Exception ex) {
            IJ.error("Error parsing .csl file: " + ex.getMessage());
            return null;
        }
        return result;
    }

    /**
     * If the file was saved with a different number of bands than the
     * current image, linearly resample the spectrum to match.
     */
    private static double[] resampleIfNeeded(double[] src, int targetBands) {
        if (src.length == targetBands) return src;
        double[] dst = new double[targetBands];
        for (int i = 0; i < targetBands; i++) {
            double pos = (double) i / (targetBands - 1) * (src.length - 1);
            int lo = (int) pos;
            int hi = Math.min(lo + 1, src.length - 1);
            double frac = pos - lo;
            dst[i] = src[lo] * (1 - frac) + src[hi] * frac;
        }
        return dst;
    }

    // Import dialog

    /**
     * Shows a preview window with the loaded spectra and a selection dialog.
     * Returns only the entries the user checked.
     */
    private static List<SpectralEntry> showImportDialog(
            List<SpectralEntry> loaded, int bands) {

        // Show spectra preview plot
        showPreviewPlot(loaded, bands);

        // Show color legend
        PostUnmixingTools.showColorLegendStatic(
                getNamesFromEntries(loaded),
                getColorsFromEntries(loaded));

        // Build selection dialog
        GenericDialog gd = new GenericDialog("Import Spectra From Library");
        gd.addMessage("Loaded " + loaded.size() + " spectrum(a) from file.\n" +
                "A preview plot and color legend have opened.\n\n" +
                "Select which spectra to import into the current library:");

        for (SpectralEntry e : loaded) {
            String colorName = PostUnmixingTools.getColorNameStatic(e.color);
            gd.addCheckbox(e.name + "  [" + colorName + "]", true);
        }

        gd.addMessage("\nYou can also rename entries after importing\n" +
                "using the Sample Spectrum action.");
        gd.showDialog();
        if (gd.wasCanceled()) return null;

        List<SpectralEntry> selected = new ArrayList<>();
        for (int i = 0; i < loaded.size(); i++) {
            if (gd.getNextBoolean()) selected.add(loaded.get(i));
        }
        if (selected.isEmpty()) {
            IJ.showMessage("No spectra selected for import.");
            return null;
        }
        IJ.log("Imported " + selected.size() + " spectra from library.");
        return selected;
    }

    private static void showPreviewPlot(List<SpectralEntry> entries, int bands) {
        double[] bandIdx = new double[bands];
        for (int b = 0; b < bands; b++) bandIdx[b] = b + 1;

        double globalMax = 0;
        for (SpectralEntry e : entries)
            for (double v : e.spectrum) if (v > globalMax) globalMax = v;
        if (globalMax < 1e-10) globalMax = 1.0;

        // Normalize for display
        Plot plot = new Plot("Library Preview - Normalized", "Spectral Band", "Normalized Intensity");
        plot.setLimits(1, bands, 0, 1.1);

        for (SpectralEntry e : entries) {
            double[] norm = e.normalized();
            plot.setColor(e.color);
            plot.addPoints(bandIdx, norm, Plot.LINE);
        }
        for (int i = 0; i < entries.size(); i++) {
            plot.setColor(entries.get(i).color);
            plot.addLabel(0.65, 0.06 + i * 0.08, entries.get(i).name);
        }
        plot.show();
    }

    private static List<String> getNamesFromEntries(List<SpectralEntry> entries) {
        List<String> names = new ArrayList<>();
        for (SpectralEntry e : entries) names.add(e.name);
        return names;
    }

    private static List<Color> getColorsFromEntries(List<SpectralEntry> entries) {
        List<Color> colors = new ArrayList<>();
        for (SpectralEntry e : entries) colors.add(e.color);
        return colors;
    }
}