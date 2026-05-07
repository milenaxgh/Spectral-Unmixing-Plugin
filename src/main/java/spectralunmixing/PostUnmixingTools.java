package spectralunmixing;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PostUnmixingTools {

    // Intensity graph 

    public static void showIntensityGraph(double[][] spectra,
                                          List<String> names,
                                          List<Color>  colors,
                                          int bands) {
        double[] bandIdx = new double[bands];
        for (int b = 0; b < bands; b++) bandIdx[b] = b + 1;

        double globalMax = 0;
        for (double[] s : spectra)
            for (double v : s) if (v > globalMax) globalMax = v;
        if (globalMax < 1e-10) globalMax = 1.0;

        Plot plot = new Plot("Unmixed Component Spectra",
                "Spectral Band", "Intensity");
        plot.setLimits(1, bands, 0, globalMax * 1.15);

        for (int i = 0; i < spectra.length; i++) {
            double[] smoothed = smooth(spectra[i]);
            plot.setColor(colors.get(i));
            plot.addPoints(bandIdx, smoothed, Plot.LINE);

            int peak = 0;
            for (int b = 1; b < bands; b++)
                if (smoothed[b] > smoothed[peak]) peak = b;
            plot.addPoints(new double[]{peak + 1},
                    new double[]{smoothed[peak]}, Plot.CIRCLE);
        }
        for (int i = 0; i < names.size(); i++) {
            plot.setColor(colors.get(i));
            plot.addLabel(0.65, 0.06 + i * 0.08, names.get(i));
        }
        plot.show();
    }

    private static double[] smooth(double[] data) {
        int n = data.length;
        double[] s = new double[n];
        s[0] = (data[0] + data[1]) / 2.0;
        s[n - 1] = (data[n - 2] + data[n - 1]) / 2.0;
        for (int i = 1; i < n - 1; i++)
            s[i] = (data[i - 1] + data[i] + data[i + 1]) / 3.0;
        return s;
    }

    // Composite builder

    public static void buildComposite(List<ImagePlus> outputs,
                                      List<String>    names,
                                      List<Color>     colors) {
        int n = outputs.size();
        if (n == 0) { IJ.error("No outputs to composite."); return; }

        // Step 1: choose which components to include 
        GenericDialog gd1 = new GenericDialog("Build Composite - Select Components");
        gd1.addMessage("Select which components to include in the composite:");
        for (int i = 0; i < n; i++)
            gd1.addCheckbox(names.get(i), true);
        gd1.showDialog();
        if (gd1.wasCanceled()) return;

        List<Integer> selected = new ArrayList<>();
        for (int i = 0; i < n; i++)
            if (gd1.getNextBoolean()) selected.add(i);

        if (selected.isEmpty()) { IJ.error("No components selected."); return; }

        // Step 2: choose color for each selected component
        String[] paletteNames = {
                "Red", "Green", "Blue", "Yellow", "Cyan",
                "Magenta", "Orange", "Pink", "White", "Custom..."
        };
        Color[] paletteColors = {
                Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN,
                Color.MAGENTA, new Color(255, 128, 0), Color.PINK, Color.WHITE, null
        };

        // Default color assignments cycle through palette
        String[] defaults = new String[selected.size()];
        for (int si = 0; si < selected.size(); si++)
            defaults[si] = paletteNames[si % (paletteNames.length - 1)];

        GenericDialog gd2 = new GenericDialog("Build Composite - Assign Colors");
        gd2.addMessage("Assign a display color to each selected component:");
        for (int si = 0; si < selected.size(); si++) {
            int idx = selected.get(si);
            gd2.addChoice(names.get(idx) + ":", paletteNames, defaults[si]);
        }
        gd2.showDialog();
        if (gd2.wasCanceled()) return;

        // Resolve chosen colors
        List<Color> chosenColors = new ArrayList<>();
        for (int si = 0; si < selected.size(); si++) {
            int choiceIdx = gd2.getNextChoiceIndex();
            if (choiceIdx < paletteColors.length - 1) {
                chosenColors.add(paletteColors[choiceIdx]);
            } else {
                // fall back to the stored color or white
                Color stored = colors.get(selected.get(si));
                chosenColors.add(stored != null ? stored : Color.WHITE);
            }
        }

        int width  = outputs.get(0).getWidth();
        int height = outputs.get(0).getHeight();

        //Normalise each selected component to [0, 1]
        float[][][] normData = new float[selected.size()][height][width];
        for (int si = 0; si < selected.size(); si++) {
            int idx = selected.get(si);
            ImageProcessor ip = outputs.get(idx).getProcessor();
            float minV = Float.MAX_VALUE, maxV = -Float.MAX_VALUE;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++) {
                    float v = ip.getPixelValue(x, y);
                    if (v < minV) minV = v;
                    if (v > maxV) maxV = v;
                }
            float range = maxV - minV;
            if (range < 1e-10f) range = 1f;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    normData[si][y][x] = (ip.getPixelValue(x, y) - minV) / range;
        }

        // Build RGB composite
        int[] rgbPixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double R = 0, G = 0, B = 0;
                for (int si = 0; si < selected.size(); si++) {
                    Color  c = chosenColors.get(si);
                    double v = normData[si][y][x];
                    R += v * c.getRed();
                    G += v * c.getGreen();
                    B += v * c.getBlue();
                }
                int r = Math.min(255, (int) R);
                int g = Math.min(255, (int) G);
                int b = Math.min(255, (int) B);
                rgbPixels[y * width + x] = (r << 16) | (g << 8) | b;
            }
        }

        ColorProcessor cp = new ColorProcessor(width, height, rgbPixels);
        ImagePlus composite = new ImagePlus("Composite", cp);
        composite.show();

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        composite.getWindow().setLocation(
                (screen.width  - composite.getWindow().getWidth())  / 2,
                (screen.height - composite.getWindow().getHeight()) / 2);

        StringBuilder log = new StringBuilder("Composite built: ");
        for (int si = 0; si < selected.size(); si++) {
            log.append(names.get(selected.get(si)))
                    .append("=")
                    .append(getColorNameStatic(chosenColors.get(si)));
            if (si < selected.size() - 1) log.append(", ");
        }
        IJ.log(log.toString());
        // Composite spectra graph
        showCompositeSpectra(outputs, selected, chosenColors, names);
    }
    // Color name

    public static String getColorNameStatic(Color c) {
        if (c.getRed() > 200 && c.getGreen() < 80  && c.getBlue() < 80)  return "Red";
        if (c.getGreen() > 150 && c.getRed() < 80  && c.getBlue() < 80)  return "Green";
        if (c.getBlue() > 200 && c.getRed() < 80   && c.getGreen() < 80) return "Blue";
        if (c.getRed() > 200 && c.getGreen() > 200 && c.getBlue() < 80)  return "Yellow";
        if (c.getGreen() > 150 && c.getBlue() > 150 && c.getRed() < 50)  return "Cyan";
        if (c.getRed() > 150 && c.getBlue() > 150  && c.getGreen() < 50) return "Magenta";
        if (c.getRed() > 150 && c.getGreen() > 100 && c.getBlue() < 50)  return "Orange";
        return String.format("RGB(%d,%d,%d)", c.getRed(), c.getGreen(), c.getBlue());
    }
    
    private static void showCompositeSpectra(List<ImagePlus> outputs,
                                             List<Integer>   selected,
                                             List<Color>     chosenColors,
                                             List<String>    names) {
        // Build mean spectrum per selected component from its abundance image
        int width  = outputs.get(0).getWidth();
        int height = outputs.get(0).getHeight();
        int bands  = 0; // used pixel values not spectral bands 

        // Actually show the stored endmember spectra if available from ResultStore
        // Fall back to showing a simple intensity plot of the abundance maps
        int n = selected.size();
        double[] idx = new double[n];
        double[] vals = new double[n];
        for (int si = 0; si < n; si++) {
            idx[si]  = si + 1;
            // Mean abundance value as a proxy for contribution
            ImageProcessor ip = outputs.get(selected.get(si)).getProcessor();
            double sum = 0;
            for (int y = 0; y < height; y++)
                for (int x = 0; x < width; x++)
                    sum += ip.getPixelValue(x, y);
            vals[si] = sum / (width * height);
        }

        Plot plot = new Plot("Composite Components - Mean Abundance",
                "Component", "Mean Abundance");
        plot.setLimits(0.5, n + 0.5, 0, 1.05);
        for (int si = 0; si < n; si++) {
            plot.setColor(chosenColors.get(si));
            plot.addPoints(new double[]{idx[si]}, new double[]{vals[si]}, Plot.BAR);
            plot.addLabel((si * (1.0/n)) + 0.02, 0.06 + si * 0.08,
                    names.get(selected.get(si)));
        }
        plot.show();
    }

    public static void showColorLegendStatic(List<String> namesFromEntries, List<Color> colorsFromEntries) {
    }
}