# Spectral Unmixing Plugin for Fiji/ImageJ

An open-source Fiji/ImageJ plugin for spectral unmixing of hyperspectral microscopy images.
The plugin implements two mathematically distinct unmixing approaches — ROI-based linear unmixing using least-squares optimisation with an optional non-negativity constraint, and automatic endmember detection via Vertex Component Analysis — making it suitable for both guided and exploratory analysis of hyperspectral microscopy data. Built entirely in Java with no external dependencies, it integrates seamlessly into the Fiji ecosystem, supports the native .im3 file format through Bio-Formats, and provides a complete workflow from spectral library construction to composite RGB output. The tool lowers the barrier to quantitative multispectral image analysis for any biological or biomedical imaging laboratory, regardless of their instrumentation or budget.

---

## Features

- **Linear Unmixing (ROI-based)** — Sample spectra from pure regions, compute endmembers via Manual Compute, solve per pixel using OLS or NNLS
- **PCA / VCA Unmixing** — Automatic endmember detection via Vertex Component Analysis with scree plot and component selection
- **Spectral Library** — Build, save, and reload spectral libraries as portable `.csl` files
- **Composite Image Builder** — Blend selected components into a colour RGB image with per-component colour assignment
- **Single Panel UI** — All functions accessible from one persistent window
- **No external dependencies** — Pure Java, uses only the ImageJ1 API bundled with Fiji
- **Bio-Formats compatible** — Reads `.im3` (PerkinElmer Nuance) and any multi-band stack

---

## Requirements

| | Version |
|---|---|
| Fiji | Any recent release |
| Java | 8 or higher |
| Bio-Formats | Bundled with Fiji |

---

## Installation

1. Download `Spectral_Unmixing.jar` from the [Releases](../../releases) page
2. Copy it into your Fiji `plugins` folder:
   - **Windows:** `C:\Fiji.app\plugins\`
   - **macOS:** `/Applications/Fiji.app/plugins/`
   - **Linux:** `~/Fiji.app/plugins/`
3. Restart Fiji or go to **Help › Refresh Menus**
4. The plugin appears under **Plugins › Spectral Unmixing**

---

## Building from Source

```bash
git clone https://github.com/your-username/spectral-unmixing-fiji
cd spectral-unmixing-fiji
mvn clean package -DskipTests
```

The compiled JAR will be at `target/Spectral_Unmixing-1.0.0.jar`.

### Prerequisites

- Java JDK 8+
- Maven 3.6+

---

## Usage

### Opening the panel

1. Open your hyperspectral image stack in Fiji (drag and drop, or **File › Open**)
2. For `.im3` files: use **Plugins › Bio-Formats › Bio-Formats Importer**, leave settings at defaults
3. Open the plugin: **Plugins › Spectral Unmixing › Open Panel**
4. Click **Refresh** in the panel to load the current image

---

### Linear Unmixing (ROI-based)


1. Draw ROIs over spectrally pure regions on the image
2. Press **T** after each ROI to add it to the ROI Manager
3. Click **Sample ROI(s)** — name each spectrum and assign a colour
4. *(Optional)* Click **Manual Compute** to extract a pure spectrum from a mixed region:
   - Select a **Known** (pure) spectrum and a **Mixed** spectrum
   - The plugin computes: `Pure = Mixed − α × Known`
   - α is found automatically by golden-section search minimising negative values
5. Click **Show Spectra** to verify the library looks correct
6. Click **Run Unmixing** (or **Unmix Image**)
   - Select which library entries to use
   - Enable **Non-negativity constraint (NNLS)** for physically valid results
7. One 32-bit abundance image opens per endmember

---

### PCA / VCA Unmixing

Implements Vertex Component Analysis (Nascimento & Dias, IEEE 2005):

1. Switch mode to **PCA / VCA Unmixing** in the panel
2. Enter the number of components to extract
3. Optionally enable **Scree plot** to inspect variance before choosing
4. Click **Run PCA / VCA**
5. Name and assign a colour to each component when prompted
6. Component abundance images open in a tiled grid

---

### Building a Composite Image

After running either unmixing method:

1. Click **Build Composite...**
2. Select which components to include
3. Assign a display colour to each selected component (Red, Green, Blue, Cyan, etc.)
4. A composite RGB image opens — each component blended by its assigned colour

The composite can also be triggered any time from:
**Plugins › Spectral Unmixing › Build Composite...**

---

### Spectral Library Files (.csl)

Libraries can be saved and reloaded across sessions:

- **Export .csl** — saves the current library to a plain-text file
- **Import .csl** — loads spectra from a file with a preview plot and selection dialog
- If the saved band count differs from the current image, spectra are linearly resampled to match

`.csl` format:
```
#CSL v1 bands=28
NAME=Pure_Hematoxylin
COLOR=0,0,255
SPECTRUM=1234.56,1345.67,...
END
```

---

## File Structure

```
src/main/java/sc/fiji/spectralunmixing/
├── SpectralUnmixing_.java          Entry point + menu items
├── SpectralUnmixingPanel_.java     Single-window Swing UI panel
├── SpectralEntry.java              Data container for one library spectrum
├── LinearUnmixingManager.java      ROI sampling, Manual Compute, OLS/NNLS solve
├── PCAUnmixingManager.java         VCA endmember extraction + NNLS abundance solve
├── PostUnmixingTools.java          Intensity graph + composite builder
├── SpectraIO.java                  Export/import .csl spectral library files
├── ResultStore.java                Static store for last unmixing result
├── MatrixUtils.java                Transpose, multiply, pseudoinverse, covariance
├── JacobiEigen.java                Jacobi iterative eigen-decomposition
└── NNLS.java                       Lawson-Hanson non-negative least squares solver
```

---

## Algorithms

| Algorithm | Used in | Reference |
|---|---|---|
| Ordinary Least Squares (OLS) | Linear Unmixing | Moore, 1920 |
| Lawson-Hanson NNLS | Linear Unmixing, VCA | Lawson & Hanson, 1974 |
| Golden-Section Search | Manual Compute (alpha optimisation) | Kiefer, 1953 |
| Gauss-Jordan Pseudoinverse | OLS pre-factor | Strang, 2016 |
| Jacobi Eigen-Decomposition | VCA dimensionality reduction | Golub & Van Loan, 2013 |
| Vertex Component Analysis (VCA) | PCA/VCA mode | Nascimento & Dias, 2005 |

---

## Menu Entries

```
Plugins > Spectral Unmixing > Spectral Unmixing...   (mode dialog, no panel)
Plugins > Spectral Unmixing > Open Panel             (full single-window UI)
Plugins > Spectral Unmixing > Build Composite...     (composite from last result)
```

---

## References

[1] Golub, G.H. & Van Loan, C.F. Matrix Computations, 4th ed. Johns Hopkins University Press, 2013.
[2] Kiefer, J. (1953). Minimax search for a maximum. Proceedings of the American Mathematical Society, 4, 502–506.
[3] Lawson, C.L. & Hanson, R.J. Solving Least Squares Problems. SIAM Classics in Applied Mathematics, 1974.
[4] Strang, G. (2005). Linear Algebra and Its Applications, 4th ed. Cengage Learning, Boston.
[5] Nascimento, J.M.P. & Dias, J.M.B. (2005). Vertex Component Analysis: A Fast Algorithm to Unmix Hyperspectral Data. IEEE Transactions on Geoscience and Remote Sensing, 43(4).
[6] Schindelin, J. et al. (2012). Fiji: an open-source platform for biological-image analysis. Nature Methods, 9(7)

---

## License

GNU General Public License v3 or later. See [LICENSE](LICENSE) for details.

---

## Acknowledgements

Developed as an undergraduate capstone project at the American University of Armenia.
Inspired by the PerkinElmer Nuance 3.0.2 spectral unmixing software.
Special thanks to Dr. Narine Sarvazyan for her mentorship and to the Orbeli Institute of Physiology NAS RA, for HSI equipment access. I am deeply grateful to the open-source community, whose commitment to open-access tools and shared knowledge, specifically the Fiji/ImageJ framework, was instrumental in the development of this software. This work stands as a contribution to accessible, reproducible research.
