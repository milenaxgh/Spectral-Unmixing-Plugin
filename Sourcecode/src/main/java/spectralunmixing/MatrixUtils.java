package spectralunmixing;

/**
 * Lightweight matrix operations.
 * All matrices are double[][]. Row-major: M[row][col].
 */
public class MatrixUtils {

    private MatrixUtils() {}

    /** Transpose a matrix [r×c] → [c×r] */
    public static double[][] transpose(double[][] A) {
        int r = A.length, c = A[0].length;
        double[][] T = new double[c][r];
        for (int i = 0; i < r; i++)
            for (int j = 0; j < c; j++)
                T[j][i] = A[i][j];
        return T;
    }

    /** Multiply A[m×k] * B[k×n] → [m×n] */
    public static double[][] multiply(double[][] A, double[][] B) {
        int m = A.length, k = A[0].length, n = B[0].length;
        double[][] C = new double[m][n];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                for (int l = 0; l < k; l++)
                    C[i][j] += A[i][l] * B[l][j];
        return C;
    }

    /** Multiply matrix A[m×k] by vector v[k] → result[m] */
    public static double[] multiplyMv(double[][] A, double[] v) {
        int m = A.length, k = v.length;
        double[] r = new double[m];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < k; j++)
                r[i] += A[i][j] * v[j];
        return r;
    }

    /**
     * Pseudo-inverse of a square matrix via Gauss-Jordan elimination.
     */
    public static double[][] pseudoInverse(double[][] M) {
        int n = M.length;
        double[][] aug = new double[n][2 * n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) aug[i][j] = M[i][j];
            aug[i][n + i] = 1.0;
        }

        for (int col = 0; col < n; col++) {
            // Partial pivoting
            int pivotRow = col;
            double pivotVal = Math.abs(aug[col][col]);
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > pivotVal) {
                    pivotVal = Math.abs(aug[row][col]);
                    pivotRow = row;
                }
            }
            double[] tmp = aug[col]; aug[col] = aug[pivotRow]; aug[pivotRow] = tmp;

            double diag = aug[col][col];
            if (Math.abs(diag) < 1e-12) aug[col][col] = diag = 1e-8;

            for (int j = 0; j < 2 * n; j++) aug[col][j] /= diag;

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                for (int j = 0; j < 2 * n; j++)
                    aug[row][j] -= factor * aug[col][j];
            }
        }

        double[][] inv = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                inv[i][j] = aug[i][n + j];
        return inv;
    }

    /**
     * Covariance matrix C = XᵀX / (n-1).
     * X is [nPixels × bands], result is [bands × bands].
     */
    public static double[][] covarianceMatrix(double[][] X, int nPixels, int bands) {
        double[][] C = new double[bands][bands];
        double norm = 1.0 / (nPixels - 1);
        for (int p = 0; p < nPixels; p++)
            for (int i = 0; i < bands; i++)
                for (int j = i; j < bands; j++)
                    C[i][j] += X[p][i] * X[p][j];
        for (int i = 0; i < bands; i++)
            for (int j = i; j < bands; j++) {
                C[i][j] *= norm;
                C[j][i]  = C[i][j];
            }
        return C;
    }
}