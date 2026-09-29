package com.holybuckets.villageecon.util;

public class LeastSquares {

    public static final String CLASS_ID = "032";

    private static final double SINGULAR_THRESHOLD = 1e-10;

    public static class SingularMatrixException extends RuntimeException {
        public SingularMatrixException(String message) {
            super(message);
        }
    }

    private LeastSquares() { }

    public static double[] solve(double[][] x, double[] y)
    {
        if (x == null || y == null || x.length == 0 || x.length != y.length)
            throw new SingularMatrixException("Sample data is empty or mismatched");

        int rows = x.length;
        int terms = x[0].length;
        int p = terms + 1;

        if (rows < p)
            throw new SingularMatrixException("Need at least " + p + " samples, got " + rows);

        double[][] design = new double[rows][p];
        for (int i = 0; i < rows; i++) {
            design[i][0] = 1d;
            for (int j = 0; j < terms; j++)
                design[i][j + 1] = x[i][j];
        }

        double[] scale = scaleColumns(design, p);

        double[][] normal = new double[p][p];
        double[] moment = new double[p];

        for (int i = 0; i < p; i++) {
            for (int j = i; j < p; j++) {
                double sum = 0d;
                for (int r = 0; r < rows; r++)
                    sum += design[r][i] * design[r][j];
                normal[i][j] = sum;
                normal[j][i] = sum;
            }
            double sum = 0d;
            for (int r = 0; r < rows; r++)
                sum += design[r][i] * y[r];
            moment[i] = sum;
        }

        double[] beta = gaussian(normal, moment, p);

        for (int j = 0; j < p; j++)
            beta[j] /= scale[j];

        return beta;
    }

    private static double[] scaleColumns(double[][] design, int p)
    {
        double[] scale = new double[p];
        for (int j = 0; j < p; j++) {
            double max = 0d;
            for (double[] row : design)
                max = Math.max(max, Math.abs(row[j]));

            scale[j] = (max > SINGULAR_THRESHOLD) ? max : 1d;
            if (scale[j] == 1d) continue;

            for (double[] row : design)
                row[j] /= scale[j];
        }
        return scale;
    }

    private static double[] gaussian(double[][] a, double[] b, int p)
    {
        for (int col = 0; col < p; col++)
        {
            int pivot = col;
            for (int r = col + 1; r < p; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            }

            if (Math.abs(a[pivot][col]) < SINGULAR_THRESHOLD)
                throw new SingularMatrixException("Design matrix is singular at column " + col);

            double[] swapRow = a[col]; a[col] = a[pivot]; a[pivot] = swapRow;
            double swapVal = b[col]; b[col] = b[pivot]; b[pivot] = swapVal;

            for (int r = col + 1; r < p; r++) {
                double factor = a[r][col] / a[col][col];
                if (factor == 0d) continue;
                for (int c = col; c < p; c++)
                    a[r][c] -= factor * a[col][c];
                b[r] -= factor * b[col];
            }
        }

        double[] beta = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double sum = b[i];
            for (int j = i + 1; j < p; j++)
                sum -= a[i][j] * beta[j];
            beta[i] = sum / a[i][i];
        }
        return beta;
    }
}
