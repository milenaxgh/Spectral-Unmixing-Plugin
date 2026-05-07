package spectralunmixing;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.*;
import ij.plugin.frame.PlugInFrame;
import ij.plugin.frame.RoiManager;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;

/**
 * SpectralUnmixingPanel_
 * Plain Fiji-style UI.
 * Menu: Plugins > Spectral Unmixing > Open Panel
 */
public class SpectralUnmixingPanel_ extends PlugInFrame implements ActionListener {

    // ── state ─────────────────────────────────────────────────────────────────
    private final List<SpectralEntry> library = new ArrayList<>();
    private List<ImagePlus> lastOutputs = new ArrayList<>();
    private List<String>    lastNames   = new ArrayList<>();
    private List<Color>     lastColors  = new ArrayList<>();
    private double[][]      lastSpectra = null;

    static final Color[] PALETTE = {
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
            Color.CYAN, Color.MAGENTA, new Color(165,42,42),
            Color.PINK, Color.DARK_GRAY, Color.ORANGE
    };
    static final String[] PALETTE_NAMES = {
            "Red","Green","Blue","Yellow","Cyan",
            "Magenta","Brown","Pink","Dark Gray","Orange"
    };

    // ── UI ────────────────────────────────────────────────────────────────────
    private Label         lblImage;
    private java.awt.List libList;
    private Button btnSample, btnCompute, btnShowLib,
            btnExport, btnImport,  btnClear,
            btnUnmix,  btnPCA,     btnComposite;
    private Checkbox  cbNNLS, cbScree;
    private TextField tfNComp;

    private static SpectralUnmixingPanel_ instance;

    // ═════════════════════════════════════════════════════════════════════════
    public SpectralUnmixingPanel_() {
        super("Spectral Unmixing");
        instance = this;
        buildUI();
        pack();
        setResizable(false);
        GUI.center(this);
        setVisible(true);
        refreshImage();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  UI BUILD  — matches Fiji's plain gray look (Histogram, Log windows)
    // ═════════════════════════════════════════════════════════════════════════
    private void buildUI() {
        setLayout(new BorderLayout(2, 2));

        // image info bar (like "300x246 pixels; RGB; 288K")
        Panel infoBar = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        lblImage = new Label("(no image)");
        lblImage.setFont(new Font("SansSerif", Font.PLAIN, 11));
        Button btnR = new Button("Refresh");
        btnR.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnR.addActionListener(e -> refreshImage());
        infoBar.add(lblImage);
        infoBar.add(btnR);
        add(infoBar, BorderLayout.NORTH);

        // Main Panel
        Panel main = new Panel();
        main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
        main.add(sp(3));

        // Spectral Library
        main.add(sectionRow("Spectral Library"));
        libList = new java.awt.List(6, false);
        libList.setFont(new Font("Monospaced", Font.PLAIN, 11));
        libList.setPreferredSize(new Dimension(340, 100));
        Panel listWrap = new Panel(new BorderLayout());
        listWrap.add(libList, BorderLayout.CENTER);
        Panel lw2 = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        lw2.add(listWrap);
        main.add(lw2);
        main.add(sp(2));

        // library buttons — row 1
        main.add(btnRow(
                btnSample  = btn("Sample ROI(s)"),
                btnCompute = btn("Manual Compute"),
                btnShowLib = btn("Show Spectra")
        ));
        // library buttons — row 2
        main.add(btnRow(
                btnExport = btn("Export .csl"),
                btnImport = btn("Import .csl"),
                btnClear  = btn("Clear Library")
        ));
        main.add(sep());

        // Linear Unmixing
        main.add(sectionRow("Linear Unmixing (ROI-based)"));
        Panel unmixRow = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        cbNNLS = new Checkbox("Non-negative (NNLS)", true);
        cbNNLS.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnUnmix = btn("Unmix Image");
        unmixRow.add(cbNNLS);
        unmixRow.add(btnUnmix);
        main.add(unmixRow);
        main.add(sep());

        // PCA / VCA
        main.add(sectionRow("PCA / VCA Unmixing (automatic)"));
        Panel pcaRow = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        pcaRow.add(new Label("Components:"));
        tfNComp = new TextField("3", 3);
        tfNComp.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cbScree = new Checkbox("Scree plot", true);
        cbScree.setFont(new Font("SansSerif", Font.PLAIN, 11));
        btnPCA = btn("Run PCA / VCA");
        pcaRow.add(tfNComp);
        pcaRow.add(cbScree);
        pcaRow.add(btnPCA);
        main.add(pcaRow);
        main.add(sep());

        // Output
        main.add(sectionRow("Output"));
        Panel outRow = new Panel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        btnComposite = btn("Build Composite...");
        outRow.add(btnComposite);
        main.add(outRow);
        main.add(sp(4));

        add(main, BorderLayout.CENTER);

        // register listeners
        for (Button b : new Button[]{
                btnSample,btnCompute,btnShowLib,btnExport,btnImport,btnClear,
                btnUnmix,btnPCA,btnComposite
        }) b.addActionListener(this);
    }

    // widget helpers
    private Button btn(String lbl) {
        Button b = new Button(lbl);
        b.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return b;
    }

    private Panel btnRow(Button... bs) {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        for (Button b : bs) p.add(b);
        return p;
    }

    private Component sep() {
        Panel p = new Panel(new BorderLayout());
        p.setPreferredSize(new Dimension(340, 1));
        p.setBackground(new Color(160, 160, 160));
        return p;
    }

    private Component sp(int h) {
        Panel p = new Panel();
        p.setPreferredSize(new Dimension(1, h));
        return p;
    }

    private Panel sectionRow(String txt) {
        Panel p = new Panel(new FlowLayout(FlowLayout.LEFT, 4, 1));
        Label l = new Label(txt);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        p.add(l);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DISPATCH
    // ═════════════════════════════════════════════════════════════════════════
    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = ((Button) e.getSource()).getLabel();
        route(cmd);
    }

    private void dispatchShortcut(String lbl) {
        switch (lbl) {
            case "Sample":    route("Sample ROI(s)");      break;
            case "Compute":   route("Manual Compute");     break;
            case "Unmix":     route("Unmix Image");        break;
            case "PCA":       route("Run PCA / VCA");      break;
            case "Composite": route("Build Composite..."); break;
        }
    }

    private void route(String cmd) {
        switch (cmd) {
            case "Sample ROI(s)":      doSampleROI();    break;
            case "Manual Compute":     doManualCompute();break;
            case "Show Spectra":       doShowSpectra();  break;
            case "Export .csl":        doExport();       break;
            case "Import .csl":        doImport();       break;
            case "Clear Library":      doClear();        break;
            case "Unmix Image":        doUnmix();        break;
            case "Run PCA / VCA":      doPCA();          break;
            case "Build Composite...": doComposite();    break;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  IMAGE INFO
    // ═════════════════════════════════════════════════════════════════════════
    private void refreshImage() {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            lblImage.setText("(no image open)");
        } else {
            int b = SpectralUnmixing_.getBandCount(imp);
            lblImage.setText(imp.getTitle()
                    + ";  " + b + " bands;  "
                    + imp.getWidth() + "x" + imp.getHeight());
        }
        pack();
    }

    private void refreshLibList() {
        libList.removeAll();
        for (int i = 0; i < library.size(); i++) {
            SpectralEntry e = library.get(i);
            libList.add(String.format("[%d] %-18s  pk=%d",
                    i + 1, e.name, peakBand(e.spectrum)));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Image validation and retrieval helpers
    // ═════════════════════════════════════════════════════════════════════════
    private ImagePlus requireImage() {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("Spectral Unmixing", "No image open. Open your .im3 stack first.");
            return null;
        }
        if (SpectralUnmixing_.getBandCount(imp) < 2) {
            IJ.error("Spectral Unmixing", "Image must have at least 2 spectral bands.");
            return null;
        }
        return imp;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LIBRARY ACTIONS
    // ═════════════════════════════════════════════════════════════════════════
    private void doSampleROI() {
        ImagePlus imp = requireImage();
        if (imp == null) return;
        if (library.size() >= 10) { IJ.error("Library full (10 entries max)."); return; }

        RoiManager rm = RoiManager.getInstance();
        if (rm == null || rm.getCount() == 0) {
            IJ.error("No ROIs in the ROI Manager.\n"
                    + "Draw ROIs, press T after each to add, then click Sample ROI(s).");
            return;
        }

        int bands = SpectralUnmixing_.getBandCount(imp);
        Roi[] rois = rm.getRoisAsArray();
        int slots  = Math.min(rois.length, 10 - library.size());

        GenericDialog gd = new GenericDialog("Sample Spectra");
        gd.addMessage("ROI Manager: " + rois.length + " ROI(s). "
                + "Library: " + library.size() + "/10.\n"
                + "Select ROIs to add:");
        for (int i = 0; i < slots; i++) {
            String rn = rois[i].getName();
            if (rn == null || rn.isEmpty()) rn = "ROI_" + i;
            gd.addCheckbox(i + ": " + rn, true);
            gd.addStringField("Name:", "Label_" + (library.size() + i + 1), 16);
            gd.addChoice("Color:", PALETTE_NAMES,
                    PALETTE_NAMES[(library.size() + i) % PALETTE_NAMES.length]);
        }
        gd.showDialog();
        if (gd.wasCanceled()) return;

        int added = 0;
        for (int i = 0; i < slots; i++) {
            boolean ticked = gd.getNextBoolean();
            String  name   = gd.getNextString().trim();
            int     cidx   = gd.getNextChoiceIndex();
            if (!ticked) continue;
            if (library.size() >= 10) break;
            if (name.isEmpty()) name = "Label_" + (library.size() + 1);
            double[] sp = extractMeanSpectrum(imp, rois[i], bands);
            library.add(new SpectralEntry(name, sp, PALETTE[cidx % PALETTE.length]));
            IJ.log("Sampled: '" + name + "'  peak=" + peakBand(sp));
            added++;
        }
        if (added > 0) {
            refreshLibList();
            IJ.log("Added " + added + ". Library: " + library.size() + "/10");
        } else {
            IJ.showMessage("No ROIs were selected.");
        }
    }

    private void doManualCompute() {
        if (library.size() < 2) { IJ.error("Need at least 2 spectra in library."); return; }
        ImagePlus imp = requireImage();
        if (imp == null) return;
        int bands = SpectralUnmixing_.getBandCount(imp);

        String[] names = libNames();
        GenericDialog gd = new GenericDialog("Manual Compute");
        gd.addMessage("Computes:  Pure = Mixed  -  alpha * Known");
        gd.addChoice("Known:", names, names[0]);
        gd.addChoice("Mixed:", names, names.length > 1 ? names[1] : names[0]);
        gd.addCheckbox("Auto-scale alpha", true);
        gd.addCheckbox("Fit offset", false);
        gd.addStringField("Result name:", "Pure_Computed", 16);
        gd.addChoice("Color:", PALETTE_NAMES,
                PALETTE_NAMES[library.size() % PALETTE_NAMES.length]);
        gd.showDialog();
        if (gd.wasCanceled()) return;

        int ki = gd.getNextChoiceIndex(), mi = gd.getNextChoiceIndex();
        boolean auto = gd.getNextBoolean(), fit = gd.getNextBoolean();
        String nm = gd.getNextString().trim();
        int ci = gd.getNextChoiceIndex();

        if (ki == mi) { IJ.error("Known and Mixed must be different."); return; }

        double[] known = library.get(ki).spectrum;
        double[] mixed = library.get(mi).spectrum;
        double alpha = auto ? optAlpha(known, mixed, bands) : 1.0;

        double[] pure = new double[bands];
        for (int b = 0; b < bands; b++) pure[b] = mixed[b] - alpha * known[b];
        if (fit) {
            double minV = Double.MAX_VALUE;
            for (double v : pure) if (v < minV) minV = v;
            if (minV < 0) for (int b = 0; b < bands; b++) pure[b] -= minV;
        }
        for (int b = 0; b < bands; b++) if (pure[b] < 0) pure[b] = 0;
        if (nm.isEmpty()) nm = "Pure_" + (library.size() + 1);

        Color col = PALETTE[ci % PALETTE.length];
        library.add(new SpectralEntry(nm, pure, col));
        refreshLibList();
        IJ.log("Manual Compute: alpha=" + String.format("%.4f", alpha)
                + "  added: '" + nm + "'");

        // Plot
        double[] bidx = bIdx(bands);
        double max = 0;
        for (double v : mixed) if (v > max) max = v;
        for (double v : pure)  if (v > max) max = v;
        Plot p = new Plot("Manual Compute Result", "Band", "Intensity");
        p.setLimits(1, bands, 0, max * 1.15);
        p.setColor(library.get(ki).color); p.addPoints(bidx, known, Plot.LINE);
        p.setColor(library.get(mi).color); p.addPoints(bidx, mixed, Plot.LINE);
        p.setColor(col);                   p.addPoints(bidx, pure,  Plot.LINE);
        p.setColor(library.get(ki).color); p.addLabel(0.05, 0.08, library.get(ki).name + " (known)");
        p.setColor(library.get(mi).color); p.addLabel(0.05, 0.16, library.get(mi).name + " (mixed)");
        p.setColor(col);                   p.addLabel(0.05, 0.24, nm + " (computed)");
        p.show();
    }

    private void doShowSpectra() {
        if (library.isEmpty()) { IJ.showMessage("Library is empty."); return; }
        ImagePlus imp = requireImage();
        if (imp == null) return;
        int bands = SpectralUnmixing_.getBandCount(imp);
        double[] bidx = bIdx(bands);
        double max = 0;
        for (SpectralEntry e : library)
            for (double v : e.spectrum) if (v > max) max = v;
        Plot p = new Plot("Spectral Library", "Band", "Intensity");
        p.setLimits(1, bands, 0, max * 1.1);
        for (int i = 0; i < library.size(); i++) {
            SpectralEntry e = library.get(i);
            p.setColor(e.color);
            p.addPoints(bidx, e.spectrum, Plot.LINE);
            p.addLabel(0.65, 0.06 + i * 0.08, e.name);
        }
        p.show();
    }

    private void doExport() {
        if (library.isEmpty()) { IJ.error("Library is empty."); return; }
        ImagePlus imp = requireImage();
        if (imp == null) return;
        SpectraIO.exportLibrary(library, SpectralUnmixing_.getBandCount(imp));
    }

    private void doImport() {
        ImagePlus imp = requireImage();
        if (imp == null) return;
        List<SpectralEntry> imported =
                SpectraIO.importFromLibrary(SpectralUnmixing_.getBandCount(imp));
        if (imported != null) {
            for (SpectralEntry e : imported) {
                if (library.size() >= 10) {
                    IJ.showMessage("Library full at 10. Some entries skipped.");
                    break;
                }
                library.add(e);
            }
            refreshLibList();
            IJ.log("Imported. Library: " + library.size() + "/10");
        }
    }

    private void doClear() {
        library.clear();
        lastOutputs.clear(); lastNames.clear(); lastColors.clear();
        lastSpectra = null;
        ResultStore.clear();
        refreshLibList();
        IJ.log("Library cleared.");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  LINEAR UNMIXING
    // ═════════════════════════════════════════════════════════════════════════
    private void doUnmix() {
        if (library.size() < 2) { IJ.error("Need at least 2 spectra in library."); return; }
        ImagePlus imp = requireImage();
        if (imp == null) return;
        int bands = SpectralUnmixing_.getBandCount(imp);
        int W = imp.getWidth(), H = imp.getHeight();

        GenericDialog gd = new GenericDialog("Select Endmembers");
        gd.addMessage("Select spectra to use:");
        for (SpectralEntry e : library) gd.addCheckbox(e.name, e.selected);
        gd.showDialog();
        if (gd.wasCanceled()) return;

        List<SpectralEntry> active = new ArrayList<>();
        for (SpectralEntry e : library) {
            e.selected = gd.getNextBoolean();
            if (e.selected) active.add(e);
        }
        if (active.size() < 2) { IJ.error("Select at least 2."); return; }
        int nE = active.size();

        // Mixing matrix M [bands x nE]
        double[][] M = new double[bands][nE];
        for (int e = 0; e < nE; e++)
            for (int b = 0; b < bands; b++) M[b][e] = active.get(e).spectrum[b];

        // OLS pre-factor
        double[][] Mt        = MatrixUtils.transpose(M);
        double[][] olsFactor = MatrixUtils.multiply(MatrixUtils.pseudoInverse(
                MatrixUtils.multiply(Mt, M)), Mt);
        boolean useNNLS = cbNNLS.getState();

        IJ.showStatus("Unmixing...");
        float[][][] abund = new float[nE][H][W];
        ImageStack stack  = imp.getStack();

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double[] pv = new double[bands];
                for (int b = 0; b < bands; b++)
                    pv[b] = stack.getProcessor(SpectralUnmixing_.getSliceIndex(imp, b))
                            .getPixelValue(x, y);
                double[] a = useNNLS ? NNLS.solve(M, pv, 300)
                        : MatrixUtils.multiplyMv(olsFactor, pv);
                for (int e = 0; e < nE; e++) abund[e][y][x] = (float) a[e];
            }
            if (y % 40 == 0) IJ.showProgress((double) y / H);
        }
        IJ.showProgress(1.0);

        // Output windows
        lastOutputs = new ArrayList<>();
        lastNames   = new ArrayList<>();
        lastColors  = new ArrayList<>();
        lastSpectra = new double[nE][];

        placeWindows(active, abund, W, H, nE);
        for (int e = 0; e < nE; e++) lastSpectra[e] = active.get(e).spectrum;

        PostUnmixingTools.showIntensityGraph(lastSpectra, lastNames, lastColors, bands);
        IJ.showStatus("Unmixing complete (" + (useNNLS ? "NNLS" : "OLS") + ").");
        IJ.log("Unmixing done. Endmembers: " + nE
                + "  Method: " + (useNNLS ? "NNLS" : "OLS"));
    }

    // Place output windows in a grid, all visible

    private void placeWindows(List<SpectralEntry> active,
                              float[][][] abund, int W, int H, int nE) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int margin  = 8;
        int topGap  = 120;
        int availW  = screen.width  - margin * 2;
        int availH  = screen.height - topGap - margin - 60;

        int cols    = Math.min(nE, 5);
        int winW    = (availW - margin * (cols + 1)) / cols;
        int winH    = (int)(winW * ((double) H / W));

        int rows = (int) Math.ceil((double) nE / cols);

        while (rows * (winH + 30 + margin) > availH && winH > 40) {
            winH = (int)(winH * 0.85);
            winW = (int)(winH * ((double) W / H));
        }

        int gridW  = cols * (winW + margin);
        int startX = (screen.width - gridW) / 2;
        int startY = topGap;

        for (int e = 0; e < nE; e++) {
            FloatProcessor fp = new FloatProcessor(W, H);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) fp.setf(x, y, abund[e][y][x]);
            fp.resetMinAndMax();

            ImagePlus out = new ImagePlus("Abundance_" + active.get(e).name, fp);
            out.show();

            double mag = (double) winW / W;
            out.getCanvas().setMagnification(mag);

            IJ.run(out, "Grays", "");
            IJ.run(out, "Enhance Contrast", "saturated=0.35");
            addColorBorder(out, active.get(e).color);

            int col = e % cols;
            int row = e / cols;

            out.getWindow().setLocation(
                    startX + col * (winW + margin),
                    startY + row * (winH + 30 + margin));

            out.getWindow().setSize(winW + 16, winH + 50);

            lastOutputs.add(out);
            lastNames.add(active.get(e).name);
            lastColors.add(active.get(e).color);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  PCA / VCA
    // ═════════════════════════════════════════════════════════════════════════
    private void doPCA() {
        ImagePlus imp = requireImage();
        if (imp == null) return;
        int nComp;
        try {
            nComp = Integer.parseInt(tfNComp.getText().trim());
            if (nComp < 2) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            IJ.error("Enter a valid integer >= 2 in the Components field.");
            return;
        }

        // PCAUnmixingManager stores results in ResultStore
        new PCAUnmixingManager(imp, nComp, cbScree.getState()).run();

        // Pull results from ResultStore
        lastOutputs = new ArrayList<>(ResultStore.outputs);
        lastNames   = new ArrayList<>(ResultStore.names);
        lastColors  = new ArrayList<>(ResultStore.colors);
        lastSpectra = null;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  COMPOSITE
    // ═════════════════════════════════════════════════════════════════════════
    private void doComposite() {
        if (lastOutputs.isEmpty()) {
            IJ.error("No unmixing result yet. Run Linear Unmixing or PCA first.");
            return;
        }
        PostUnmixingTools.buildComposite(lastOutputs, lastNames, lastColors);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  MATH HELPERS
    // ═════════════════════════════════════════════════════════════════════════
    private double[] extractMeanSpectrum(ImagePlus imp, Roi roi, int bands) {
        double[] s = new double[bands];
        ImageStack stack = imp.getStack();
        for (int b = 0; b < bands; b++) {
            int si = SpectralUnmixing_.getSliceIndex(imp, b);
            ImageProcessor ip = stack.getProcessor(si);
            ip.setRoi(roi);
            s[b] = ip.getStatistics().mean;
        }
        return s;
    }

    private double optAlpha(double[] known, double[] mixed, int bands) {
        double lo = 0, hi = 3, gr = (Math.sqrt(5) + 1) / 2;
        for (int i = 0; i < 100; i++) {
            double c = hi - (hi - lo) / gr, d = lo + (hi - lo) / gr;
            if (alphaCost(known,mixed,c,bands) < alphaCost(known,mixed,d,bands)) hi=d; else lo=c;
            if (Math.abs(hi - lo) < 1e-6) break;
        }
        return (lo + hi) / 2;
    }

    private double alphaCost(double[] k, double[] m, double a, int bands) {
        double s = 0;
        for (int b = 0; b < bands; b++) { double v = m[b]-a*k[b]; if (v<0) s+=v*v; }
        return s;
    }

    private int peakBand(double[] sp) {
        int pk = 0;
        for (int b = 1; b < sp.length; b++) if (sp[b] > sp[pk]) pk = b;
        return pk + 1;
    }

    private double[] bIdx(int bands) {
        double[] idx = new double[bands];
        for (int b = 0; b < bands; b++) idx[b] = b + 1;
        return idx;
    }

    private String[] libNames() {
        String[] n = new String[library.size()];
        for (int i = 0; i < library.size(); i++) n[i] = library.get(i).name;
        return n;
    }

    private void addColorBorder(ImagePlus out, Color c) {
        int t = 6, w = out.getWidth(), h = out.getHeight();
        Overlay ov = new Overlay();
        for (Roi r : new Roi[]{
                new Roi(0,0,w,t), new Roi(0,h-t,w,t),
                new Roi(0,0,t,h), new Roi(w-t,0,t,h)}) {
            r.setFillColor(c); r.setStrokeColor(c); ov.add(r);
        }
        out.setOverlay(ov); out.updateAndDraw();
    }

   //
    @Override public void run(String arg) {}

    @Override
    public void windowClosing(WindowEvent e) {
        instance = null;
        super.windowClosing(e);
    }

    public static SpectralUnmixingPanel_ getInstance() { return instance; }
}