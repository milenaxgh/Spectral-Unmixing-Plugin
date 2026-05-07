package spectralunmixing;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.plugin.PlugIn;
import ij.gui.GenericDialog;

public class SpectralUnmixing_ implements PlugIn {

    @Override
    public void run(String arg) {

        //  build composite from last result
        if ("composite".equals(arg)) {
            if (ResultStore.outputs.isEmpty()) {
                IJ.error("No unmixing result available.\n" +
                        "Run Spectral Unmixing first.");
                return;
            }
            PostUnmixingTools.buildComposite(
                    ResultStore.outputs,
                    ResultStore.names,
                    ResultStore.colors);
            return;
        }

        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.error("Spectral Unmixing",
                    "No image is open.\n\n" +
                            "Open your .im3 file via:\n" +
                            "Plugins > Bio-Formats > Bio-Formats Importer");
            return;
        }

        int bands = getBandCount(imp);
        if (bands < 2) {
            IJ.error("Spectral Unmixing",
                    "Found only " + bands + " spectral band(s).\n\n" +
                            "Image info: " + describeImage(imp) + "\n\n" +
                            "Expected a stack with 2+ bands.");
            return;
        }

        GenericDialog gd = new GenericDialog("Spectral Unmixing");
        gd.addMessage("Image : " + imp.getTitle());
        gd.addMessage("Bands : " + bands + "  " + describeImage(imp));
        gd.addMessage("Size  : " + imp.getWidth() + " x " + imp.getHeight() + " px");
        String[] modes = {
                "Linear Unmixing  (ROI sampling + Manual Compute)",
                "PCA Unmixing  (automatic component detection)"
        };
        gd.addRadioButtonGroup("Select mode:", modes, 2, 1, modes[0]);
        gd.showDialog();
        if (gd.wasCanceled()) return;

        String mode = gd.getNextRadioButton();
        if (mode.equals(modes[0])) {
            new LinearUnmixingManager(imp).run();
        } else {
            new PCAUnmixingManager(imp).run();
        }
    }

    public static int getBandCount(ImagePlus imp) {
        if (imp.getNChannels() > 1) return imp.getNChannels();
        if (imp.getNSlices()   > 1) return imp.getNSlices();
        return imp.getStackSize();
    }

    public static int getSliceIndex(ImagePlus imp, int b) {
        if (imp.getNChannels() > 1) return imp.getStackIndex(b + 1, 1, 1);
        if (imp.getNSlices()   > 1) return imp.getStackIndex(1, b + 1, 1);
        return b + 1;
    }

    public static String describeImage(ImagePlus imp) {
        return "(C=" + imp.getNChannels() +
                " Z=" + imp.getNSlices()  +
                " T=" + imp.getNFrames()  +
                " stack=" + imp.getStackSize() + ")";
    }
}