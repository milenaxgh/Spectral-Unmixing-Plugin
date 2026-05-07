package spectralunmixing;

/**
 * Non-Negative Least Squares solver.
 * Lawson-Hanson active-set algorithm:
 *   minimize  ||Ax - b||²   subject to  x >= 0
 *
 * Reference: Lawson & Hanson, "Solving Least Squares Problems" (1974).
 */
public class NNLS {

    private NNLS() {}

    /**
     * Solve:  min ||A*x - b||  s.t. x >= 0
     *
     * @param A       Design matrix [m × n]  (m = bands, n = endmembers)
     * @param b       Observation vector [m]
     * @param maxIter Maximum iterations
     * @return        Non-negative solution vector x [n]
     */
    public static double[] solve(double[][] A, double[] b, int maxIter) {
        int m = A.length;
        int n = A[0].length;

        double[] x       = new double[n];   // solution, init = 0
        double[] w       = new double[n];   // gradient
        boolean[] passive = new boolean[n]; // passive set

        computeGradient(A, b, x, w, m, n);

        int iter = 0;
        while (iter < maxIter) {

            // Find index with largest positive gradient not in passive set
            int t = -1;
            double maxW = 1e-10;
            for (int j = 0; j < n; j++) {
                if (!passive[j] && w[j] > maxW) {
                    maxW = w[j];
                    t = j;
                }
            }
            if (t < 0) break;  // KKT satisfied

            passive[t] = true;

            // Inner loop: solve unconstrained LS on passive set
            double[] s = solvePassiveLS(A, b, passive, n, m);

            while (hasNonPositive(s, passive, n)) {
                double alpha = Double.MAX_VALUE;
                for (int i = 0; i < n; i++) {
                    if (passive[i] && s[i] <= 0) {
                        double candidate = x[i] / (x[i] - s[i]);
                        if (candidate < alpha) alpha = candidate;
                    }
                }
                for (int i = 0; i < n; i++)
                    x[i] = x[i] + alpha * (s[i] - x[i]);

                for (int i = 0; i < n; i++)
                    if (passive[i] && Math.abs(x[i]) < 1e-12)
                        passive[i] = false;

                s = solvePassiveLS(A, b, passive, n, m);
            }

            for (int i = 0; i < n; i++)
                if (passive[i]) x[i] = s[i];

            computeGradient(A, b, x, w, m, n);
            iter++;
        }

        // Clamp small negatives
        for (int i = 0; i < n; i++) if (x[i] < 0) x[i] = 0;
        return x;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** w = Aᵀ(b - Ax) */
    private static void computeGradient(double[][] A, double[] b,
                                        double[] x, double[] w, int m, int n) {
        double[] residual = new double[m];
        for (int i = 0; i < m; i++) {
            residual[i] = b[i];
            for (int j = 0; j < n; j++) residual[i] -= A[i][j] * x[j];
        }
        for (int j = 0; j < n; j++) {
            w[j] = 0;
            for (int i = 0; i < m; i++) w[j] += A[i][j] * residual[i];
        }
    }

    /** Solve unconstrained LS on the passive set, return full-length vector. */
    private static double[] solvePassiveLS(double[][] A, double[] b,
                                           boolean[] passive, int n, int m) {
        int p = 0;
        int[] idx = new int[n];
        for (int j = 0; j < n; j++) if (passive[j]) idx[p++] = j;
        if (p == 0) return new double[n];

        double[][] Ap = new double[m][p];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < p; j++)
                Ap[i][j] = A[i][idx[j]];

        double[][] AtA    = MatrixUtils.multiply(MatrixUtils.transpose(Ap), Ap);
        double[][] AtAInv = MatrixUtils.pseudoInverse(AtA);
        double[][] pre    = MatrixUtils.multiply(AtAInv, MatrixUtils.transpose(Ap));
        double[]   sp     = MatrixUtils.multiplyMv(pre, b);

        double[] s = new double[n];
        for (int j = 0; j < p; j++) s[idx[j]] = sp[j];
        return s;
    }

    /** True if any passive-set entry in s is <= 0 */
    private static boolean hasNonPositive(double[] s, boolean[] passive, int n) {
        for (int i = 0; i < n; i++)
            if (passive[i] && s[i] <= 0) return true;
        return false;
    }
}