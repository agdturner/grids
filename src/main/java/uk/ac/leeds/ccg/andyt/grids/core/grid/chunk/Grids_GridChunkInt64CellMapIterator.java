/**
 * Version 1.0 is to handle single variable 2DSquareCelled raster data.
 * Copyright (C) 2005 Andy Turner, CCG, University of Leeds, UK.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA.
 */
package uk.ac.leeds.ccg.andyt.grids.core.grid.chunk;

import uk.ac.leeds.ccg.andyt.grids.core.grid.Grids_GridInt;
import gnu.trove.TIntLongIterator;
import java.util.NoSuchElementException;
import uk.ac.leeds.ccg.andyt.grids.utilities.Grids_AbstractIterator;

/**
 * For iterating through the values in a Grid2DSquareCellIntChunk64CellMap
 * instance. The values are not returned in any particular order.
 */
public class Grids_GridChunkInt64CellMapIterator
        extends Grids_AbstractIterator {

    private TIntLongIterator dataIterator;
    private long iteratorIndex;
    private long numberOfCells;
    private int value;
    private long size;
    private long valueCounter;
    private int noDataValue;

    /**
     * Creates a new instance of Grid2DSquareIntIterator
     */
    public Grids_GridChunkInt64CellMapIterator() {
    }

    /**
     * Creates a new instance of Grid2DSquareIntIterator
     *
     * @param chunk The Grid2DSquareCellIntChunk64CellMap to iterate over.
     */
    public Grids_GridChunkInt64CellMapIterator(
            Grids_GridChunkInt64CellMap chunk) {
        this.dataIterator = chunk.getData().iterator();
        iteratorIndex = 0L;
        Grids_GridInt g = chunk.getGrid();
        this.noDataValue = g.getNoDataValue(
                ge.HandleOutOfMemoryErrorFalse);
        long chunkNrows = (long) g.getChunkNRows(chunk.ChunkID,
                ge.HandleOutOfMemoryErrorFalse);
        long chunkNcols = (long) g.getChunkNCols(chunk.ChunkID,
                ge.HandleOutOfMemoryErrorFalse);
        this.numberOfCells = chunkNrows * chunkNcols;
        try { // In case empty!
            this.dataIterator.advance();
            this.value = dataIterator.key();
            this.valueCounter = 1L;
            this.size = this.dataIterator.value();
        } catch (NoSuchElementException nsee0) {
            this.value = this.noDataValue;
            //this.valueCounter = this.numberOfCells;
        }
    }

    /**
     * Returns <tt>true</tt> if the iteration has more elements. (In other
     * words, returns <tt>true</tt> if <tt>next</tt> would return an element
     * rather than throwing an exception.)
     *
     * @return <tt>true</tt> if the iterator has more elements.
     */
    @Override
    public boolean hasNext() {
        return iteratorIndex + 2L < numberOfCells;
    }

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration.
     * @exception NoSuchElementException iteration has no more elements.
     */
    @Override
    public Object next() {
        Integer next = this.value;
        this.iteratorIndex++;
        try {
            if (this.value != noDataValue) {
                if (this.valueCounter == this.size) {
                    try { // In case no more!
                        this.dataIterator.advance();
                        this.value = dataIterator.key();
                        this.valueCounter = 1;
                        this.size = this.dataIterator.value();
                    } catch (NoSuchElementException nsee0) {
                        this.value = noDataValue;
                    }
                } else {
                    this.valueCounter++;
                }
            }
        } catch (Exception e0) {
            // Should be last element!
            //e0.printStackTrace();
        }
        return next;
    }

    /**
     *
     * Removes from the underlying collection the last element returned by the
     * iterator (optional operation). This method can be called only once per
     * call to <tt>next</tt>. The behavior of an iterator is unspecified if the
     * underlying collection is modified while the iteration is in progress in
     * any way other than by calling this method.
     *
     * @exception UnsupportedOperationException if the <tt>remove</tt>
     * operation is not supported by this Iterator.
     *
     * @exception IllegalStateException if the <tt>next</tt> method has not yet
     * been called, or the <tt>remove</tt> method has already been called after
     * the last call to the <tt>next</tt>
     * method.
     */
    @Override
    public void remove() {
    }

}