package spectralunmixing;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.process.FloatProcessor;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PCAUnmixingManager {

    private final ImagePlus imp;
    private final int width, height, bands, nPixels;
    private final int  presetNComp;
    private final boolean presetShowScree;

    static final Color[] PC_COLORS = {
            new Color(0, 120, 215),
            new Color(220, 50,  50),
            new Color(0,  160, 80),
            new Color(160, 0,  200),
            new Color(200, 120, 0),
            new Color(0,  180, 200),
            new Color(196, 67, 133),
            new Color(80,  80,  180),
            new Color(0,  140, 100),
            new Color(180, 140, 0)
    };

    static final String[] COLOR_NAMES = {
            "Blue","Red","Green","Purple","Orange",
            "Cyan","Pink","Indigo","Teal","Gold"
    };

    // Constructors

    public PCAUnmixingManager(ImagePlus imp) {
        this(imp, -1, true);
    }

    /**
     * Called from the panel
     */
    public PCAUnmixingManager(ImagePlus imp, int nComp, boolean showScree) {
        this.imp             = imp;
        this.width           = imp.getWidth();
        this.height          = imp.getHeight();
        this.bands           = SpectralUnmixing_.getBandCount(imp);
        this.nPixels         = width * height;
        this.presetNComp     = nComp;
        this.presetShowScree = showScree;
    }

    // Main entry point
    public void run() {
        IJ.log("PCA/VCA Unmixing started. Bands: " + bands
                + "  " + SpectralUnmixing_.describeImage(imp));

        // Step 1: build data matrix
        IJ.showStatus("PCA: loading data...");
        double[][] X = buildDataMatrix();

        // Step 2: mean-center
        IJ.showStatus("PCA: mean-centering...");
        double[] means = new double[bands];
        for (int b = 0; b < bands; b++) {
            double sum = 0;
            for (int p = 0; p < nPixels; p++) sum += X[p][b];
            means[b] = sum / nPixels;
        }
        for (int p = 0; p < nPixels; p++)
            for (int b = 0; b < bands; b++)
                X[p][b] -= means[b];

        // Step 3: covariance
        IJ.showStatus("PCA: computing covariance...");
        double[][] C = MatrixUtils.covarianceMatrix(X, nPixels, bands);

        // Step 4: eigen-decomposition
        IJ.showStatus("PCA: eigen-decomposition...");
        double[][] vecs = new double[bands][bands];
        double[]   vals = new double[bands];
        JacobiEigen.decompose(C, vals, vecs, bands);
        sortDesc(vals, vecs);

        // variance
        double total = 0;
        for (double v : vals) total += Math.abs(v);
        double[] expVar = new double[bands];
        for (int i = 0; i < bands; i++)
            expVar[i] = 100.0 * Math.abs(vals[i]) / total;

        // Step 5: number of components
        int nComp;
        if (presetNComp > 0) {
            if (presetShowScree) showScreePlot(expVar);
            nComp = Math.max(2, Math.min(presetNComp, bands));
            IJ.log("Components (from panel): " + nComp);
        } else {
            nComp = askComponents(vals, expVar);
            if (nComp <= 0) return;
        }

        // Step 6: ask names and colors
        String[] compNames  = new String[nComp];
        Color[]  compColors = new Color[nComp];
        if (!askNamesAndColors(nComp, compNames, compColors)) return;

        // Step 7: VCA endmember extraction
        IJ.showStatus("PCA/VCA: finding endmembers...");
        double[][] endmembers = runVCA(X, nComp, vecs, vals);

        // Step 8: NNLS per pixel
        IJ.showStatus("PCA/VCA: solving abundances per pixel...");
        float[][][] abundances = solveAbundances(endmembers, nComp);

        // Step 9: output windows
        IJ.showStatus("PCA/VCA: building output images...");

        int screenW = Toolkit.getDefaultToolkit().getScreenSize().width;
        int winW    = Math.min(width / 2, (screenW - 20) / Math.min(nComp, 4));
        int winH    = (int)(winW * ((double)height / width));
        int cols    = Math.max(1, (screenW - 20) / (winW + 10));

        List<ImagePlus> outputs    = new ArrayList<>();
        List<String>    outNames   = new ArrayList<>();
        List<Color>     outColors  = new ArrayList<>();
        double[][]      pcaSpectra = endmembers;

        for (int c = 0; c < nComp; c++) {
            FloatProcessor fp = new FloatProcessor(width, height);
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    fp.setf(x, y, abundances[c][y][x]);
            fp.resetMinAndMax();

            String title = compNames[c] + " - " + imp.getTitle();
            ImagePlus out = new ImagePlus(title, fp);
            out.show();
            IJ.run(out, "Grays", "");
            IJ.run(out, "Enhance Contrast", "saturated=0.35");
            addColorBorder(out, compColors[c]);

            int col = c % cols, row = c / cols;
            out.getWindow().setLocation(10 + col * (winW + 10), 80 + row * (winH + 30));
            out.getWindow().setSize(winW + 16, winH + 50);

            outputs.add(out);
            outNames.add(compNames[c]);
            outColors.add(compColors[c]);

            IJ.showProgress((c + 1.0) / nComp);
        }

        // Step 10: variance plot + intensity graph
        showVariancePlot(expVar, nComp);
        PostUnmixingTools.showIntensityGraph(pcaSpectra, outNames, outColors, bands);

        // Step 11: store globally
        ResultStore.store(outputs, outNames, outColors);

        IJ.log("PCA/VCA complete. Use Build Composite when ready.");
        double cum = 0;
        for (int c = 0; c < nComp; c++) {
            cum += expVar[c];
            IJ.log(String.format("  Component %d: var=%.2f%%  cum=%.2f%%",
                    c + 1, expVar[c], cum));
        }
        IJ.showStatus("PCA/VCA complete.");
        IJ.showProgress(1.0);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  VCA — Vertex Component Analysis
    // ═════════════════════════════════════════════════════════════════════════
    private double[][] runVCA(double[][] X, int nComp,
                              double[][] vecs, double[] vals) {
        Random rng = new Random(42);
        int reducedDim = Math.min(nComp, bands);

        // Project Xc onto top nComp eigenvectors → Xr [nPixels x reducedDim]
        double[][] Xr = new double[nPixels][reducedDim];
        for (int p = 0; p < nPixels; p++)
            for (int d = 0; d < reducedDim; d++)
                for (int b = 0; b < bands; b++)
                    Xr[p][d] += X[p][b] * vecs[b][d];

        // VCA main loop
        double[][] A      = new double[reducedDim][nComp];
        int[]      endIdx = new int[nComp];

        // Initialise A with a random unit vector
        double[] f0 = randomUnit(reducedDim, rng);
        for (int d = 0; d < reducedDim; d++) A[d][0] = f0[d];

        for (int c = 0; c < nComp; c++) {
            int usedCols = Math.max(1, c);

            // Build sub-matrices for the projector using only columns 0..usedCols-1
            double[][] Asub = subMatrix(A, reducedDim, usedCols);
            double[][] AtA  = smallMul(transpose2D(Asub, reducedDim, usedCols),
                    Asub, usedCols, reducedDim, usedCols);
            double[][] AtAi = MatrixUtils.pseudoInverse(AtA);
            double[][] proj = smallMul(smallMul(Asub, AtAi, reducedDim, usedCols, usedCols),
                    transpose2D(Asub, reducedDim, usedCols),
                    reducedDim, usedCols, reducedDim);

            // Random unit vector orthogonalised against A
            double[] f     = randomUnit(reducedDim, rng);
            double[] Af    = new double[reducedDim];
            for (int d = 0; d < reducedDim; d++)
                for (int d2 = 0; d2 < reducedDim; d2++)
                    Af[d] += proj[d][d2] * f[d2];
            double[] fOrth = new double[reducedDim];
            for (int d = 0; d < reducedDim; d++) fOrth[d] = f[d] - Af[d];
            normalise(fOrth);

            // Find pixel with max |projection| onto fOrth
            double maxP = -Double.MAX_VALUE;
            int    maxI = 0;
            for (int p = 0; p < nPixels; p++) {
                double proj2 = 0;
                for (int d = 0; d < reducedDim; d++) proj2 += Xr[p][d] * fOrth[d];
                if (Math.abs(proj2) > maxP) { maxP = Math.abs(proj2); maxI = p; }
            }
            endIdx[c] = maxI;
            for (int d = 0; d < reducedDim; d++) A[d][c] = Xr[maxI][d];

            IJ.showProgress((c + 1.0) / nComp);
        }

        // Recover original (non-centred) spectra at endmember pixel locations
        double[][] endmembers = new double[nComp][bands];
        ImageStack stack = imp.getStack();
        for (int c = 0; c < nComp; c++) {
            int p  = endIdx[c];
            int px = p % width;
            int py = p / width;
            for (int b = 0; b < bands; b++) {
                int si = SpectralUnmixing_.getSliceIndex(imp, b);
                endmembers[c][b] = stack.getProcessor(si).getPixelValue(px, py);
            }
        }
        return endmembers;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  NNLS per pixel
    // ═════════════════════════════════════════════════════════════════════════
    private float[][][] solveAbundances(double[][] endmembers, int nComp) {
        double[][] M = new double[bands][nComp];
        for (int c = 0; c < nComp; c++)
            for (int b = 0; b < bands; b++) M[b][c] = endmembers[c][b];

        float[][][] abund = new float[nComp][height][width];
        int total = width * height, done = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double[] p = new double[bands];
                for (int b = 0; b < bands; b++) {
                    int si = SpectralUnmixing_.getSliceIndex(imp, b);
                    p[b] = imp.getStack().getProcessor(si).getPixelValue(x, y);
                }
                double[] a = NNLS.solve(M, p, 300);
                for (int c = 0; c < nComp; c++) abund[c][y][x] = (float) a[c];
                done++;
            }
            if (y % 30 == 0) IJ.showProgress((double) done / total);
        }
        IJ.showProgress(1.0);
        return abund;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Dialogs
    // ═════════════════════════════════════════════════════════════════════════
    private int askComponents(double[] vals, double[] expVar) {
        GenericDialog gd1 = new GenericDialog("PCA Options");
        gd1.addMessage("Bands: " + bands + "   Pixels: " + nPixels);
        gd1.addCheckbox("Show scree plot before selecting", true);
        gd1.showDialog();
        if (gd1.wasCanceled()) return -1;
        if (gd1.getNextBoolean()) showScreePlot(expVar);

        double meanVal = 0;
        for (double v : vals) meanVal += Math.abs(v);
        meanVal /= bands;
        int kaiser = 0;
        for (double v : vals) if (Math.abs(v) >= meanVal) kaiser++;
        kaiser = Math.max(2, Math.min(kaiser, bands));

        StringBuilder sb = new StringBuilder("Cumulative variance:\n");
        double cum = 0;
        for (int i = 0; i < Math.min(bands, 15); i++) {
            cum += expVar[i];
            sb.append(String.format("  PC%2d: %5.2f%%  (cum: %6.2f%%)\n",
                    i + 1, expVar[i], cum));
        }
        if (bands > 15) sb.append("  ... (" + (bands - 15) + " more)");

        GenericDialog gd2 = new GenericDialog("PCA - Number of Components");
        gd2.addMessage("Kaiser rule suggests: " + kaiser + " component(s).\n");
        gd2.addMessage(sb.toString());
        gd2.addSlider("Components to retain:", 2, Math.min(bands, 20), kaiser);
        gd2.showDialog();
        if (gd2.wasCanceled()) return -1;
        return Math.max(2, Math.min((int) gd2.getNextNumber(), bands));
    }

    private boolean askNamesAndColors(int nComp, String[] names, Color[] colors) {
        GenericDialog gd = new GenericDialog("Name Components");
        gd.addMessage("Assign a name and border color to each component:");
        for (int c = 0; c < nComp; c++) {
            gd.addStringField("Component " + (c + 1) + ":", "Label_" + (c + 1), 20);
            gd.addChoice("  Color:", COLOR_NAMES, COLOR_NAMES[c % COLOR_NAMES.length]);
        }
        gd.showDialog();
        if (gd.wasCanceled()) return false;
        for (int c = 0; c < nComp; c++) {
            names[c]  = gd.getNextString().trim();
            if (names[c].isEmpty()) names[c] = "Label_" + (c + 1);
            colors[c] = PC_COLORS[gd.getNextChoiceIndex() % PC_COLORS.length];
        }
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Plots
    // ═════════════════════════════════════════════════════════════════════════
    private void showScreePlot(double[] expVar) {
        int n = Math.min(bands, 30);
        double[] idx = new double[n], vars = new double[n], cumV = new double[n];
        double cum = 0;
        for (int i = 0; i < n; i++) {
            idx[i] = i + 1; vars[i] = expVar[i];
            cum += expVar[i]; cumV[i] = cum;
        }
        Plot plot = new Plot("PCA Scree Plot", "Principal Component", "Variance (%)");
        plot.setLimits(1, n, 0, 105);
        plot.setColor(new Color(15, 52, 96));
        plot.addPoints(idx, vars, Plot.BAR);
        plot.setColor(Color.RED);
        plot.addPoints(idx, cumV, Plot.LINE);
        plot.addPoints(idx, cumV, Plot.CIRCLE);
        plot.setColor(Color.BLACK);
        plot.addLabel(0.6, 0.10, "Bar = per-component");
        plot.addLabel(0.6, 0.18, "Red = cumulative");
        plot.show();
    }

    private void showVariancePlot(double[] expVar, int nComp) {
        double[] idx = new double[nComp], vars = new double[nComp], cumV = new double[nComp];
        double cum = 0;
        for (int i = 0; i < nComp; i++) {
            idx[i] = i + 1; vars[i] = expVar[i];
            cum += expVar[i]; cumV[i] = cum;
        }
        Plot plot = new Plot("PCA - Variance Explained", "Component", "Variance (%)");
        plot.setLimits(0.5, nComp + 0.5, 0, 105);
        plot.setColor(new Color(15, 52, 96));
        plot.addPoints(idx, vars, Plot.BAR);
        plot.setColor(Color.RED);
        plot.addPoints(idx, cumV, Plot.LINE);
        plot.addPoints(idx, cumV, Plot.CIRCLE);
        plot.setColor(Color.BLACK);
        plot.addLabel(0.55, 0.10, "Bar = per-component");
        plot.addLabel(0.55, 0.18, "Red = cumulative");
        plot.show();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Border overlay
    // ═════════════════════════════════════════════════════════════════════════
    private void addColorBorder(ImagePlus out, Color c) {
        int t = 6, w = out.getWidth(), h = out.getHeight();
        ij.gui.Overlay ov = new ij.gui.Overlay();
        for (ij.gui.Roi r : new ij.gui.Roi[]{
                new ij.gui.Roi(0, 0, w, t),
                new ij.gui.Roi(0, h - t, w, t),
                new ij.gui.Roi(0, 0, t, h),
                new ij.gui.Roi(w - t, 0, t, h)}) {
            r.setFillColor(c); r.setStrokeColor(c); ov.add(r);
        }
        out.setOverlay(ov);
        out.updateAndDraw();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Math helpers
    // ═════════════════════════════════════════════════════════════════════════
    private double[][] buildDataMatrix() {
        double[][] X = new double[nPixels][bands];
        ImageStack stack = imp.getStack();
        for (int b = 0; b < bands; b++) {
            int si = SpectralUnmixing_.getSliceIndex(imp, b);
            float[] pix = (float[]) stack.getProcessor(si).convertToFloat().getPixels();
            for (int p = 0; p < nPixels; p++) X[p][b] = pix[p];
        }
        return X;
    }

    private double[] randomUnit(int dim, Random rng) {
        double[] v = new double[dim]; double n = 0;
        for (int d = 0; d < dim; d++) { v[d] = rng.nextGaussian(); n += v[d]*v[d]; }
        n = Math.sqrt(n);
        if (n > 1e-12) for (int d = 0; d < dim; d++) v[d] /= n;
        return v;
    }

    private void normalise(double[] v) {
        double n = 0;
        for (double x : v) n += x*x;
        n = Math.sqrt(n);
        if (n > 1e-12) for (int d = 0; d < v.length; d++) v[d] /= n;
    }

    /** Extract columns 0..cols-1 from A [rows x fullCols] */
    private double[][] subMatrix(double[][] A, int rows, int cols) {
        double[][] S = new double[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) S[i][j] = A[i][j];
        return S;
    }

    private double[][] smallMul(double[][] A, double[][] B, int m, int k, int n) {
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                for (int l = 0; l < k; l++) C[i][j] += A[i][l] * B[l][j];
        return C;
    }

    private double[][] transpose2D(double[][] A, int rows, int cols) {
        double[][] T = new double[cols][rows];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++) T[j][i] = A[i][j];
        return T;
    }

    private void sortDesc(double[] vals, double[][] vecs) {
        for (int i = 1; i < bands; i++) {
            double kv = vals[i]; double[] ke = vecs[i].clone(); int j = i - 1;
            while (j >= 0 && Math.abs(vals[j]) < Math.abs(kv)) {
                vals[j+1] = vals[j]; vecs[j+1] = vecs[j].clone(); j--;
            }
            vals[j+1] = kv; vecs[j+1] = ke;
        }
    }
}