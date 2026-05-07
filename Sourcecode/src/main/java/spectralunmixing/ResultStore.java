package spectralunmixing;

import ij.ImagePlus;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the last unmixing result (from either Linear or PCA mode)
 * so it can be accessed by the Build Composite menu entry at any time.
 */
public class ResultStore {
    public static List<ImagePlus> outputs = new ArrayList<>();
    public static List<String>    names   = new ArrayList<>();
    public static List<Color>     colors  = new ArrayList<>();

    public static void store(List<ImagePlus> o, List<String> n, List<Color> c) {
        outputs = new ArrayList<>(o);
        names   = new ArrayList<>(n);
        colors  = new ArrayList<>(c);
    }

    public static void clear() {
        outputs.clear();
        names.clear();
        colors.clear();
    }
}