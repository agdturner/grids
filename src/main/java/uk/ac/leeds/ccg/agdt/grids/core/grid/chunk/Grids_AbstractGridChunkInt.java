/*
 * Copyright 2019 Andy Turner, University of Leeds.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.leeds.ccg.agdt.grids.core.grid.chunk;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import uk.ac.leeds.ccg.agdt.grids.core.grid.Grids_GridInt;
import uk.ac.leeds.ccg.agdt.grids.core.Grids_2D_ID_int;
import uk.ac.leeds.ccg.agdt.grids.core.grid.stats.Grids_InterfaceStats;

/**
 * Provides general methods and controls what methods extended classes must
 * implement acting as an interface.
*
 * @author Andy Turner
 * @version 1.0.0
 */
public abstract class Grids_AbstractGridChunkInt
        extends Grids_AbstractGridChunkNumber
        implements Serializable, Grids_InterfaceStats {

    //private static final long serialVersionUID = 1L;
    protected Grids_AbstractGridChunkInt() {
    }

    protected Grids_AbstractGridChunkInt(
            Grids_GridInt g,
            Grids_2D_ID_int chunkID) {
        super(g, chunkID);
    }

    /**
     * @return (Grids_GridInt) Grid;
     */
    @Override
    public final Grids_GridInt getGrid() {
        return (Grids_GridInt) Grid;
    }

    /**
     * Returns the value at row, col.
     *
     * @param row The row of the cell w.r.t. the origin of this chunk
     * @param col The column of the cell w.r.t. the origin of this chunk
     * @return
     */
    public abstract int getCell(int row, int col);

    /**
     * Returns the value at row, col as a double.
     *
     * @param row the row of the cell w.r.t. the origin of this chunk.
     * @param col the column of the cell w.r.t. the origin of this chunk.
     * @return
     */
    @Override
    public double getCellDouble(int row, int col) {
        return getCell(row, col);
    }

    /**
     * Initialises the value at position given by: row, col.
     *
     * @param row the row of the cell w.r.t. the origin of this chunk
     * @param col the column of the cell w.r.t. the origin of this chunk
     * @param v the value with which the cell is initialised
     */
    public abstract void initCell(int row, int col, int v);

    /**
     * Returns the value at position given by: chunk cell row chunkRow; chunk
     * cell row chunkCol and sets it to valueToSet
     *
     * @param row the row index of the cell w.r.t. the origin of this chunk
     * @param col the column index of the cell w.r.t. the origin of this chunk
     * @param valueToSet the value the cell is to be set to
     * @return
     */
    public abstract int setCell(int row, int col, int valueToSet);

    /**
     * Returns values in row major order as an int[].
     *
     * @return
     */
    protected int[] toArrayIncludingNoDataValues() {
        Grids_GridInt g = getGrid();
        int chunkNrows = g.getChunkNRows();
        int chunkNcols = g.getChunkNCols();
        long nChunkCells = (long) chunkNrows * (long) chunkNcols;
        int[] array;
        if (nChunkCells > Integer.MAX_VALUE) {
            //throw new PrecisionExcpetion
            System.out.println(
                    "PrecisionException in "
                    + this.getClass().getName() + ".toArray()!");
            System.out.println(
                    "Warning! The returned array size is only "
                    + Integer.MAX_VALUE + " instead of " + nChunkCells);
        }
        //int noDataValue = getGrid().getNoDataValue();
        array = new int[chunkNrows * chunkNcols];
        int row;
        int col;
        int count = 0;
        for (row = 0; row < chunkNrows; row++) {
            for (col = 0; col < chunkNcols; col++) {
                array[count] = getCell(row, col);
                count++;
            }
        }
        return array;
    }

    /**
     * Returns values (except those that are noDataValues) in row major order as
     * an int[].
     *
     * @return
     */
    protected int[] toArrayNotIncludingNoDataValues() {
        Grids_GridInt g = getGrid();
        int chunkNrows = g.getChunkNRows(ChunkID);
        int chunkNcols = g.getChunkNCols(ChunkID);
        int noDataValue = g.getNoDataValue();
        long n = getN();
        if (n != (int) n) {
            throw new Error("n != (int) n");
        }
        int[] array = new int[(int) n];
        int row;
        int col;
        int count = 0;
        int value;
        for (row = 0; row < chunkNrows; row++) {
            for (col = 0; col < chunkNcols; col++) {
                value = getCell(row, col);
                if (value != noDataValue) {
                    array[count] = value;
                    count++;
                }
            }
        }
        return array;
    }

    /**
     * For returning the number of cells with noDataValues as a BigInteger.
     *
     * @return
     */
    @Override
    public Long getN() {
        long n = 0;
        Grids_GridInt g = getGrid();
        int nrows = g.getChunkNRows(ChunkID);
        int ncols = g.getChunkNCols(ChunkID);
        int noDataValue = g.getNoDataValue();
        for (int row = 0; row < nrows; row++) {
            for (int col = 0; col < ncols; col++) {
                if (getCell(row, col) != noDataValue) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * For returning the sum of all data values as a BigDecimal.
     *
     * @return
     */
    @Override
    public BigDecimal getSum() {
        BigDecimal sum = new BigDecimal(0.0d);
        Grids_GridInt g = getGrid();
        int nrows = g.getChunkNRows(ChunkID);
        int ncols = g.getChunkNCols(ChunkID);
        int noDataValue = g.getNoDataValue();
        int value;
        int row;
        int col;
        for (row = 0; row < nrows; row++) {
            for (col = 0; col < ncols; col++) {
                value = getCell(row, col);
                if (value != noDataValue) {
                    sum = sum.add(new BigDecimal(value));
                }
            }
        }
        return sum;
    }

    /**
     * For returning the minimum of all data values.
     *
     * @return
     */
    public Integer getMin() {
        int min = Integer.MAX_VALUE;
        Grids_GridInt g = getGrid();
        int nrows = g.getChunkNRows();
        int ncols = g.getChunkNCols();
        int noDataValue = g.getNoDataValue();
        int value;
        int row;
        int col;
        for (row = 0; row < nrows; row++) {
            for (col = 0; col < ncols; col++) {
                value = getCell(row, col);
                if (value != noDataValue) {
                    min = Math.min(min, value);
                }
            }
        }
        return min;
    }

    /**
     * For returning the maximum of all data values.
     *
     * @return
     */
    protected Integer getMax() {
        int max = Integer.MIN_VALUE;
        Grids_GridInt g = getGrid();
        int nrows = g.getChunkNRows(ChunkID);
        int ncols = g.getChunkNCols(ChunkID);
        int noDataValue = g.getNoDataValue();
        int value;
        int row;
        int col;
        for (row = 0; row < nrows; row++) {
            for (col = 0; col < ncols; col++) {
                value = getCell(row, col);
                if (value != noDataValue) {
                    max = Math.max(max, value);
                }
            }
        }
        return max;
    }

    /**
     * For returning the mode of all data values as a HashSet&LT;Integer&GT;.
     * TODO: Better to use toArray and go through a sorted version?
     *
     * @return
     */
    protected HashSet<Integer> getMode() {
        HashSet<Integer> mode = new HashSet<>();
        long n = getN();
        if (n > 0) {
            Grids_GridInt g = getGrid();
            int nrows = g.getChunkNRows(ChunkID);
            int ncols = g.getChunkNCols(ChunkID);
            int noDataValue = g.getNoDataValue();
            int p;
            int q;
            Object[] tmode = initMode(nrows, ncols, noDataValue);
            if (tmode[0] == null) {
                return mode;
            } else {
                int value;
                long count;
                long modeCount = (Long) tmode[0];
                mode.add((Integer) tmode[1]);
                Grids_2D_ID_int chunkCellID = (Grids_2D_ID_int) tmode[2];
                // Do remainder of the row
                p = chunkCellID.getRow();
                for (q = chunkCellID.getCol() + 1; q < ncols; q++) {
                    value = getCell(p, q);
                    if (value != noDataValue) {
                        count = count(p, q, nrows, ncols, value);
                        if (count > modeCount) {
                            mode.clear();
                            mode.add(value);
                            modeCount = count;
                        } else {
                            if (count == modeCount) {
                                mode.add(value);
                            }
                        }
                    }
                }
                // Do remainder of the grid
                for (p++; p < nrows; p++) {
                    for (q = 0; q < ncols; q++) {
                        value = getCell(p, q);
                        if (value != noDataValue) {
                            count = count(p, q, nrows, ncols, value);
                            if (count > modeCount) {
                                mode.clear();
                                mode.add(value);
                                modeCount = count;
                            } else {
                                if (count == modeCount) {
                                    mode.add(value);
                                }
                            }
                        }
                    }
                }
            }
        }
        return mode;
    }

    /**
     * Initialises the mode.
     *
     * @see getModeTIntHashSet()
     */
    private Object[] initMode(
            int nrows,
            int ncols,
            int noDataValue) {
        Object[] initMode = new Object[3];
        long modeCount;
        int p;
        int q;
        int row;
        int col;
        int value;
        int thisValue;
        for (p = 0; p < nrows; p++) {
            for (q = 0; q < ncols; q++) {
                value = getCell(p, q);
                if (value != noDataValue) {
                    modeCount = 0L;
                    for (row = 0; row < nrows; row++) {
                        for (col = 0; col < ncols; col++) {
                            thisValue = getCell(row, col);
                            if (thisValue == value) {
                                modeCount++;
                            }
                        }
                    }
                    initMode[0] = modeCount;
                    initMode[1] = value;
                    initMode[2] = new Grids_2D_ID_int(p, q);
                    return initMode;
                }
            }
        }
        return initMode;
    }

    /**
     * @param p the row index of the cell from which counting starts
     * @param q the column index of the cell from which counting starts
     * @param nrows
     * @param ncols
     * @param value the value to be counted
     * @return A count of cells with value = value starting from p, q.
     */
    protected long count(
            int p,
            int q,
            int nrows,
            int ncols,
            int value) {
        long count = 1L;
        int thisValue;
        // Do remainder of the row
        for (q++; q < ncols; q++) {
            thisValue = getCell(p, q);
            if (thisValue == value) {
                count++;
            }
        }
        // Do remainder of the grid
        for (p++; p < nrows; p++) {
            for (q = 0; q < ncols; q++) {
                thisValue = getCell(p, q);
                if (thisValue == value) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Sorts the specified sub-array of doubles into ascending order. Source
     * copied from java.util.Arrays and method changed so not static for
     * performance reasons.
     *
     * @param x
     * @param len
     * @param off
     */
    protected void sort1(int x[], int off, int len) {
        // Insertion sort on smallest arrays
        if (len < 7) {
            for (int i = off; i < len + off; i++) {
                for (int j = i; j > off && x[j - 1] > x[j]; j--) {
                    swap(x, j, j - 1);
                }
            }
            return;
        }
        // Choose a partition element, v
        int m = off + (len >> 1);       // Small arrays, middle element
        if (len > 7) {
            int l = off;
            int n = off + len - 1;
            if (len > 40) {        // Big arrays, pseudomedian of 9
                int s = len / 8;
                l = med3(x, l, l + s, l + 2 * s);
                m = med3(x, m - s, m, m + s);
                n = med3(x, n - 2 * s, n - s, n);
            }
            m = med3(x, l, m, n); // Mid-size, med of 3
        }
        double v = x[m];
        // Establish Invariant: v* (<v)* (>v)* v*
        int a = off, b = a, c = off + len - 1, d = c;
        while (true) {
            while (b <= c && x[b] <= v) {
                if (x[b] == v) {
                    swap(x, a++, b);
                }
                b++;
            }
            while (c >= b && x[c] >= v) {
                if (x[c] == v) {
                    swap(x, c, d--);
                }
                c--;
            }
            if (b > c) {
                break;
            }
            swap(x, b++, c--);
        }
        // Swap partition elements back to middle
        int s, n = off + len;
        s = Math.min(a - off, b - a);
        vecswap(x, off, b - s, s);
        s = Math.min(d - c, n - d - 1);
        vecswap(x, b, n - s, s);
        // Recursively sort non-partition-elements
        if ((s = b - a) > 1) {
            sort1(x, off, s);
        }
        if ((s = d - c) > 1) {
            sort1(x, n - s, s);
        }
    }

    /**
     * Swaps x[a] with x[b]. Source copied from java.util.Arrays and method
     * changed so not static for performance reasons.
     */
    private void swap(int x[], int a, int b) {
        int t = x[a];
        x[a] = x[b];
        x[b] = t;
    }

    /**
     * Swaps x[a .. (a+n-1)] with x[b .. (b+n-1)]. Source copied from
     * java.util.Arrays and method changed so not static for performance
     * reasons.
     */
    private void vecswap(int x[], int a, int b, int n) {
        for (int i = 0; i < n; i++, a++, b++) {
            swap(x, a, b);
        }
    }

    /**
     * Returns the index of the median of the three indexed doubles. Source
     * copied from java.util.Arrays and method changed so not static for
     * performance reasons.
     */
    private int med3(int x[], int a, int b, int c) {
        return (x[a] < x[b]
                ? (x[b] < x[c] ? b : x[a] < x[c] ? c : a)
                : (x[b] > x[c] ? b : x[a] > x[c] ? c : a));
    }

    /**
     * Returns the median of all non noDataValues as a double. This method
     * requires that all data in chunk can be stored as a new array.
     *
     * @return
     */
    @Override
    public double getMedianDouble() {
        long n = getN();
        BigInteger n2 = BigInteger.valueOf(n);
        if (n > 0) {
            int[] array = toArrayNotIncludingNoDataValues();
            sort1(array, 0, array.length);
            BigInteger[] n2DAR2 = n2.divideAndRemainder(new BigInteger("2"));
            if (n2DAR2[1].compareTo(BigInteger.ZERO) == 0) {
                int index = n2DAR2[0].intValue();
                return (array[index] + array[index - 1]) / 2.0d;
            } else {
                int index = n2DAR2[0].intValue();
                return array[index];
            }
        } else {
            return getGrid().getNoDataValue();
        }
    }
    
    /**
     * Returns the standard deviation of all data values as a double.
     *
     * @return
     */
    @Override
    protected double getStandardDeviationDouble() {
        double sd = 0.0d;
        double mean = getArithmeticMeanDouble();
        Grids_GridInt g = getGrid();
        int nrows = g.getChunkNRows(ChunkID);
        int ncols = g.getChunkNCols(ChunkID);
        int noDataValue = g.getNoDataValue();
        int value;
        double count = 0.0d;
        for (int row = 0; row < nrows; row++) {
            for (int col = 0; col < ncols; col++) {
                value = getCell(row, col);
                if (value != noDataValue) {
                    sd += (value - mean) * (value - mean);
                    count += 1.0d;
                }
            }
        }
        if ((count - 1.0d) > 0.0d) {
            return Math.sqrt(sd / (count - 1.0d));
        } else {
            return sd;
        }
    }
}