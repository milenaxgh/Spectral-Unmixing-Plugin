package spectralunmixing;

/**
 * Jacobi iterative eigen-decomposition for real symmetric matrices.
 * Suitable for covariance matrices (bands ≤ 64). No external dependencies.
 *
 * Reference: Golub & Van Loan, "Matrix Computations", 4th ed.
 */
public class JacobiEigen {

    private JacobiEigen() {}

    /**
     * Decompose symmetric matrix A (n×n).
     *
     * @param A            Input symmetric matrix [n×n] (will be overwritten)
     * @param eigenvalues  Output eigenvalues [n]
     * @param eigenvectors Output eigenvectors [n×n], column c = vector for eigenvalues[c]
     * @param n            Matrix dimension
     */
    public static void decompose(double[][] A, double[] eigenvalues,
                                 double[][] eigenvectors, int n) {
        // Working copy
        double[][] D = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                D[i][j] = A[i][j];

        // Initialize eigenvectors to identity
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                eigenvectors[i][j] = (i == j) ? 1.0 : 0.0;

        final int    MAX_SWEEPS = 100;
        final double EPSILON    = 1e-12;

        for (int sweep = 0; sweep < MAX_SWEEPS; sweep++) {

            // Off-diagonal Frobenius norm
            double offNorm = 0;
            for (int i = 0; i < n - 1; i++)
                for (int j = i + 1; j < n; j++)
                    offNorm += D[i][j] * D[i][j];
            if (Math.sqrt(2.0 * offNorm) < EPSILON) break;

            for (int p = 0; p < n - 1; p++) {
                for (int q = p + 1; q < n; q++) {

                    double Dpq = D[p][q];
                    if (Math.abs(Dpq) < EPSILON * (Math.abs(D[p][p]) + Math.abs(D[q][q])))
                        continue;

                    double theta = 0.5 * (D[q][q] - D[p][p]) / Dpq;
                    double t     = Math.signum(theta) /
                            (Math.abs(theta) + Math.sqrt(theta * theta + 1.0));
                    double c     = 1.0 / Math.sqrt(t * t + 1.0);
                    double s     = t * c;
                    double tau   = s / (1.0 + c);

                    D[p][p] -= t * Dpq;
                    D[q][q] += t * Dpq;
                    D[p][q]  = 0.0;
                    D[q][p]  = 0.0;

                    for (int r = 0; r < n; r++) {
                        if (r == p || r == q) continue;
                        double Drp = D[r][p] - s * (D[r][q] + tau * D[r][p]);
                        double Drq = D[r][q] + s * (D[r][p] - tau * D[r][q]);
                        D[r][p] = D[p][r] = Drp;
                        D[r][q] = D[q][r] = Drq;
                    }

                    for (int r = 0; r < n; r++) {
                        double Vrp = eigenvectors[r][p] - s * (eigenvectors[r][q] + tau * eigenvectors[r][p]);
                        double Vrq = eigenvectors[r][q] + s * (eigenvectors[r][p] - tau * eigenvectors[r][q]);
                        eigenvectors[r][p] = Vrp;
                        eigenvectors[r][q] = Vrq;
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) eigenvalues[i] = D[i][i];
    }
}