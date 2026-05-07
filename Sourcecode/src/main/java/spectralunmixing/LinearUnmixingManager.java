package spectralunmixing;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class LinearUnmixingManager {

    private final ImagePlus imp;
    private final int width, height, bands;
    private final List<SpectralEntry> library = new ArrayList<>();
    private List<ImagePlus> lastOutputs  = new ArrayList<>();
    private List<String>    lastNames    = new ArrayList<>();
    private List<Color>     lastColors   = new ArrayList<>();
    private double[][]      lastSpectra  = null;

    static final Color[] PALETTE = {
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
            Color.CYAN, Color.MAGENTA, new Color(165,42,42),
            Color.PINK, Color.BLACK, Color.WHITE
    };
    static final String[] PALETTE_NAMES = {
            "Red","Green","Blue","Yellow","Cyan","Magenta","Brown","Pink","Black","White"
    };

    public LinearUnmixingManager(ImagePlus imp) {
        this.imp    = imp;
        this.width  = imp.getWidth();
        this.height = imp.getHeight();
        this.bands  = SpectralUnmixing_.getBandCount(imp);
    }

    public void run() {
        IJ.log("Linear Unmixing started. Bands: " + bands
                + "  " + SpectralUnmixing_.describeImage(imp));
        showWorkflowDialog();
    }

    // Main loop

    private void showWorkflowDialog() {
        while (true) {
            GenericDialog gd = new GenericDialog("Spectral Unmixing - Library");
            gd.addMessage(buildLibrarySummary());
            String[] actions = {
                    "Sample spectrum from ROI",
                    "Manual Compute (known + mixed -> pure)",
                    "Show spectra plot",
                    "Unmix image",
                    "Export library to .csl file",
                    "Import spectra from .csl library",
                    "Clear library"
            };
            gd.addRadioButtonGroup("Action:", actions, 7, 1, actions[0]);
            gd.showDialog();
            if (gd.wasCanceled()) return;

            String action = gd.getNextRadioButton();

            if (action.equals(actions[0])) {
                sampleSpectrumFromROI();
            } else if (action.equals(actions[1])) {
                showManualComputeDialog();
            } else if (action.equals(actions[2])) {
                showSpectraPlot();
            } else if (action.equals(actions[3])) {
                if (library.size() < 2) {
                    IJ.error("Need at least 2 spectra in the library.");
                    continue;
                }
                unmixImage();
            } else if (action.equals(actions[4])) {
                SpectraIO.exportLibrary(library, bands);
            } else if (action.equals(actions[5])) {
                List<SpectralEntry> imported = SpectraIO.importFromLibrary(bands);
                if (imported != null) {
                    for (SpectralEntry e : imported) {
                        if (library.size() >= 10) {
                            IJ.showMessage("Library full (10 max). Stopped at 10.");
                            break;
                        }
                        library.add(e);
                    }
                    IJ.log("Library now has " + library.size() + " entries.");
                }
            } else {
                library.clear();
                ResultStore.clear();
                IJ.log("Library cleared.");
            }
        }
    }

    private String buildLibrarySummary() {
        if (library.isEmpty())
            return "Library: (empty)\nDraw an ROI and sample it to begin.";
        StringBuilder sb = new StringBuilder(
                "Library (" + library.size() + " entries):\n");
        for (int i = 0; i < library.size(); i++)
            sb.append(String.format("  [%d] %s\n", i + 1, library.get(i).name));
        return sb.toString();
    }

    // Sample spectrum from ROI

    private void sampleSpectrumFromROI() {

        // Always read exclusively from ROI Manager
        RoiManager rm = RoiManager.getInstance();
        if (rm == null || rm.getCount() == 0) {
            IJ.error("No ROIs in the ROI Manager.\n\n" +
                    "Draw ROIs on the image, press T after each\n" +
                    "to add them to the ROI Manager, then run again.");
            return;
        }

        Roi[] available = rm.getRoisAsArray();
        int slots = Math.min(available.length, 10 - library.size());
        if (slots <= 0) {
            IJ.error("Library is full (10 entries maximum).");
            return;
        }

        GenericDialog gd = new GenericDialog("Sample Spectra from ROIs");
        gd.addMessage("Found " + available.length + " ROI(s) in ROI Manager.\n" +
                "Library: " + library.size() + "/10 entries used.\n" +
                "Select which ROIs to add:");

        for (int i = 0; i < slots; i++) {
            Roi    r     = available[i];
            String rName = (r.getName() != null && !r.getName().isEmpty())
                    ? r.getName() : "ROI_" + i;
            gd.addCheckbox("ROI " + i + ":  " + rName, true);
            gd.addStringField("  Name:", "Label_" + (library.size() + i + 1), 20);
            gd.addChoice("  Color:",
                    PALETTE_NAMES,
                    PALETTE_NAMES[(library.size() + i) % PALETTE_NAMES.length]);
        }

        gd.showDialog();
        if (gd.wasCanceled()) return;

        int added = 0;
        for (int i = 0; i < slots; i++) {
            boolean checked  = gd.getNextBoolean();
            String  name     = gd.getNextString().trim();
            int     colorIdx = gd.getNextChoiceIndex();

            if (!checked) continue;
            if (library.size() >= 10) {
                IJ.showMessage("Library full at 10 entries. Remaining ROIs skipped.");
                break;
            }
            if (name.isEmpty()) name = "Label_" + (library.size() + 1);
            Color    color    = PALETTE[colorIdx % PALETTE.length];
            double[] spectrum = extractMeanSpectrum(available[i]);
            library.add(new SpectralEntry(name, spectrum, color));
            IJ.log("Sampled: '" + name + "'  peak band=" + getPeakBand(spectrum));
            added++;
        }

        if (added > 0)
            IJ.log("Added " + added + " spectra. Library: " + library.size() + "/10.");
        else
            IJ.showMessage("No ROIs were selected.");
    }

    // Manual Compute

    private void showManualComputeDialog() {
        if (library.size() < 2) {
            IJ.error("Need at least 2 spectra in the library.\n" +
                    "Sample a pure region and a mixed region first.");
            return;
        }

        String[] names = new String[library.size()];
        for (int i = 0; i < library.size(); i++) names[i] = library.get(i).name;

        GenericDialog gd = new GenericDialog("Manual Compute Spectra");
        gd.addMessage("Computes:  Pure = Mixed - (alpha x Known)");
        gd.addChoice("Known spectrum:", names, names[0]);
        gd.addChoice("Mixed spectrum:", names, names.length > 1 ? names[1] : names[0]);
        gd.addCheckbox("Auto-scale alpha (recommended)", true);
        gd.addCheckbox("Fit offset (remove baseline)", false);
        int nextColor = library.size() % PALETTE_NAMES.length;
        gd.addStringField("Name for result:", "Pure_Computed", 25);
        gd.addChoice("Color:", PALETTE_NAMES, PALETTE_NAMES[nextColor]);
        gd.showDialog();
        if (gd.wasCanceled()) return;

        int     knownIdx  = gd.getNextChoiceIndex();
        int     mixedIdx  = gd.getNextChoiceIndex();
        boolean autoScale = gd.getNextBoolean();
        boolean fitOffset = gd.getNextBoolean();
        String  pureName  = gd.getNextString().trim();
        int     colorIdx  = gd.getNextChoiceIndex();

        if (knownIdx == mixedIdx) {
            IJ.error("Known and Mixed must be different spectra.");
            return;
        }

        double[] known = library.get(knownIdx).spectrum;
        double[] mixed = library.get(mixedIdx).spectrum;
        double alpha = autoScale ? findOptimalAlpha(known, mixed) : 1.0;
        IJ.log("Manual Compute: alpha = " + String.format("%.4f", alpha));

        double[] pure = new double[bands];
        for (int b = 0; b < bands; b++)
            pure[b] = mixed[b] - alpha * known[b];

        if (fitOffset) {
            double minVal = Double.MAX_VALUE;
            for (double v : pure) if (v < minVal) minVal = v;
            if (minVal < 0)
                for (int b = 0; b < bands; b++) pure[b] -= minVal;
        }
        for (int b = 0; b < bands; b++) if (pure[b] < 0) pure[b] = 0;

        if (pureName.isEmpty()) pureName = "Pure_" + (library.size() + 1);
        Color pureColor = PALETTE[colorIdx % PALETTE.length];

        library.add(new SpectralEntry(pureName, pure, pureColor));
        IJ.log("Added to library: '" + pureName + "'");

        showComputedSpectraPlot(known, mixed, pure,
                library.get(knownIdx).name,
                library.get(mixedIdx).name,
                pureName,
                library.get(knownIdx).color,
                library.get(mixedIdx).color,
                pureColor, alpha);
    }

    private double findOptimalAlpha(double[] known, double[] mixed) {
        double lo = 0.0, hi = 3.0;
        double gr = (Math.sqrt(5) + 1) / 2;
        double c  = hi - (hi - lo) / gr;
        double d  = lo + (hi - lo) / gr;
        for (int i = 0; i < 100; i++) {
            if (costAlpha(known, mixed, c) < costAlpha(known, mixed, d)) hi = d;
            else lo = c;
            c = hi - (hi - lo) / gr;
            d = lo + (hi - lo) / gr;
            if (Math.abs(hi - lo) < 1e-6) break;
        }
        return (lo + hi) / 2;
    }

    private double costAlpha(double[] known, double[] mixed, double alpha) {
        double cost = 0;
        for (int b = 0; b < bands; b++) {
            double v = mixed[b] - alpha * known[b];
            if (v < 0) cost += v * v;
        }
        return cost;
    }

    // Unmix image

    private void unmixImage() {
        GenericDialog gd = new GenericDialog("Unmix - Select Endmembers");
        gd.addMessage("Select spectra to unmix with:");
        for (SpectralEntry e : library)
            gd.addCheckbox(e.name, e.selected);
        gd.addCheckbox("Non-negative constraint (NNLS) - recommended", true);
        gd.showDialog();
        if (gd.wasCanceled()) return;

        List<SpectralEntry> active = new ArrayList<>();
        for (SpectralEntry e : library) {
            e.selected = gd.getNextBoolean();
            if (e.selected) active.add(e);
        }
        boolean useNNLS = gd.getNextBoolean();

        if (active.size() < 2) {
            IJ.error("Select at least 2 spectra.");
            return;
        }

        int nE = active.size();

        double[][] M = new double[bands][nE];
        for (int e = 0; e < nE; e++) {
            double[] spec = active.get(e).spectrum;
            for (int b = 0; b < bands; b++) M[b][e] = spec[b];
        }

        double[][] MtM       = MatrixUtils.multiply(MatrixUtils.transpose(M), M);
        double[][] MtMInv    = MatrixUtils.pseudoInverse(MtM);
        double[][] olsFactor = MatrixUtils.multiply(MtMInv, MatrixUtils.transpose(M));

        IJ.showStatus("Unmixing...");
        float[][][] abundances = new float[nE][height][width];
        ImageStack stack = imp.getStack();
        int total = width * height, done = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] p = new double[bands];
                for (int b = 0; b < bands; b++) {
                    int si = SpectralUnmixing_.getSliceIndex(imp, b);
                    p[b] = stack.getProcessor(si).getPixelValue(x, y);
                }
                double[] a = useNNLS
                        ? NNLS.solve(M, p, 300)
                        : MatrixUtils.multiplyMv(olsFactor, p);
                for (int e = 0; e < nE; e++)
                    abundances[e][y][x] = (float) a[e];
                done++;
            }
            if (y % 30 == 0) IJ.showProgress((double) done / total);
        }
        IJ.showProgress(1.0);

        // Output windows
        int screenW = Toolkit.getDefaultToolkit().getScreenSize().width;
        int startX = 10, startY = 80;
        int winW = Math.min(width / 2, (screenW - 20) / Math.min(nE, 4));
        int winH = (int)(winW * ((double)height / width));
        int cols  = Math.max(1, (screenW - 20) / (winW + 10));

        List<ImagePlus> outputs   = new ArrayList<>();
        List<String>    outNames  = new ArrayList<>();
        List<Color>     outColors = new ArrayList<>();
        double[][]      outSpectra = new double[nE][];

        for (int e = 0; e < nE; e++) {
            FloatProcessor fp = new FloatProcessor(width, height);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    fp.setf(x, y, abundances[e][y][x]);
            fp.resetMinAndMax();

            ImagePlus out = new ImagePlus("Abundance_" + active.get(e).name, fp);
            out.show();
            IJ.run(out, "Grays", "");
            IJ.run(out, "Enhance Contrast", "saturated=0.35");
            addColorBorder(out, active.get(e).color);

            int col = e % cols;
            int row = e / cols;
            out.getWindow().setLocation(startX + col * (winW + 10),
                    startY + row * (winH + 30));
            out.getWindow().setSize(winW + 16, winH + 50);

            outputs.add(out);
            outNames.add(active.get(e).name);
            outColors.add(active.get(e).color);
            outSpectra[e] = active.get(e).spectrum;

        }

        // Store for later composite
        lastOutputs = outputs;
        lastNames   = outNames;
        lastColors  = outColors;
        lastSpectra = outSpectra;

        ResultStore.store(outputs, outNames, outColors);

        // Show intensity graph
        PostUnmixingTools.showIntensityGraph(outSpectra, outNames, outColors, bands);

        IJ.log("Unmixing complete. Method: " + (useNNLS ? "NNLS" : "OLS"));
        for (SpectralEntry e : active) IJ.log("  -> " + e.name);
        IJ.showStatus("Unmixing complete. Use Plugins > Spectral Unmixing > Build Composite... when ready.");
    }

    // Helpers

    private double[] extractMeanSpectrum(Roi roi) {
        double[] spectrum = new double[bands];
        ImageStack stack  = imp.getStack();
        for (int b = 0; b < bands; b++) {
            int si = SpectralUnmixing_.getSliceIndex(imp, b);
            ImageProcessor ip = stack.getProcessor(si);
            ip.setRoi(roi);
            spectrum[b] = ip.getStatistics().mean;
        }
        return spectrum;
    }

    private int getPeakBand(double[] spectrum) {
        int peak = 0;
        for (int b = 1; b < spectrum.length; b++)
            if (spectrum[b] > spectrum[peak]) peak = b;
        return peak + 1;
    }

    private String suggestName(int i) {
        return "Label_" + (i + 1);
    }

    private void addColorBorder(ImagePlus out, Color c) {
        int thickness = 6;
        int w = out.getWidth(), h = out.getHeight();
        Overlay ov = new Overlay();
        Roi top   = new Roi(0, 0, w, thickness);
        Roi bot   = new Roi(0, h - thickness, w, thickness);
        Roi left  = new Roi(0, 0, thickness, h);
        Roi right = new Roi(w - thickness, 0, thickness, h);
        for (Roi r : new Roi[]{top, bot, left, right}) {
            r.setFillColor(c);
            r.setStrokeColor(c);
            ov.add(r);
        }
        out.setOverlay(ov);
        out.updateAndDraw();
    }

    void showSpectraPlot() {
        if (library.isEmpty()) { IJ.showMessage("Library is empty."); return; }
        double[] bandIdx = new double[bands];
        for (int b = 0; b < bands; b++) bandIdx[b] = b + 1;

        double globalMax = 0;
        for (SpectralEntry e : library)
            for (double v : e.spectrum) if (v > globalMax) globalMax = v;

        Plot plot = new Plot("Spectral Library", "Band index", "Intensity");
        plot.setLimits(1, bands, 0, globalMax * 1.1);
        for (int i = 0; i < library.size(); i++) {
            SpectralEntry e = library.get(i);
            plot.setColor(e.color);
            plot.addPoints(bandIdx, e.spectrum, Plot.LINE);
            plot.addLabel(0.65, 0.06 + i * 0.07, e.name);
        }
        plot.show();
    }

    private void showComputedSpectraPlot(double[] known, double[] mixed, double[] pure,
                                         String kName, String mName, String pName,
                                         Color kColor, Color mColor, Color pColor, double alpha) {
        double[] bandIdx = new double[bands];
        for (int b = 0; b < bands; b++) bandIdx[b] = b + 1;
        double max = 0;
        for (double v : mixed) if (v > max) max = v;
        for (double v : pure)  if (v > max) max = v;
        Plot plot = new Plot("Manual Compute Result", "Band index", "Intensity");
        plot.setLimits(1, bands, 0, max * 1.15);
        plot.setColor(kColor); plot.addPoints(bandIdx, known, Plot.LINE);
        plot.setColor(mColor); plot.addPoints(bandIdx, mixed, Plot.LINE);
        plot.setColor(pColor); plot.addPoints(bandIdx, pure,  Plot.LINE);
        plot.setColor(kColor); plot.addLabel(0.05, 0.08, kName + " (known)");
        plot.setColor(mColor); plot.addLabel(0.05, 0.16, mName + " (mixed)");
        plot.setColor(pColor); plot.addLabel(0.05, 0.24,
                pName + " (computed, alpha=" + String.format("%.3f", alpha) + ")");
        plot.show();
    }
}