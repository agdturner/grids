/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.leeds.ccg.andyt.grids.core.grid.chunk;

import uk.ac.leeds.ccg.andyt.grids.core.grid.Grids_AbstractGrid;
import java.io.Serializable;
import uk.ac.leeds.ccg.andyt.grids.core.Grids_2D_ID_int;
import uk.ac.leeds.ccg.andyt.grids.core.Grids_Environment;
import uk.ac.leeds.ccg.andyt.grids.core.Grids_Object;
import uk.ac.leeds.ccg.andyt.grids.utilities.Grids_AbstractIterator;

/**
 *
 * @author geoagdt
 */
public abstract class Grids_AbstractGridChunk extends Grids_Object implements Serializable {

    /**
     * A reference to the Grid.
     */
    protected transient Grids_AbstractGrid Grid;
    /**
     * For storing the Grids_2D_ID_int of this. TODO: Is this transient for
     * caching?
     */
    protected transient Grids_2D_ID_int ChunkID;
    //protected Grids_2D_ID_int _ChunkID;
    /**
     * Indicator for whether the swapped version of this chunk is upToDate.
     * TODO: This adds a small amount of weight, so for 64CellMap
     * implementations it may be undesirable?
     */
    protected transient boolean SwapUpToDate;
    //protected boolean SwapUpToDate;

    public Grids_AbstractGridChunk() {
    }

    protected Grids_AbstractGridChunk(Grids_Environment ge) {
        super(ge);
    }

    /**
     * Returns this._Grid2DSquareCell.
     *
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public Grids_AbstractGrid getGrid(boolean handleOutOfMemoryError) {
        try {
            Grids_AbstractGrid result = getGrid();
            result.ge.tryToEnsureThereIsEnoughMemoryToContinue(result, handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return Grids_AbstractGridChunk.this.getGrid(handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns Grid.
     *
     * @return
     */
    protected Grids_AbstractGrid getGrid() {
        return Grid;
    }

    /**
     * Initialises _Grid2DSquareCell.
     *
     * @param g
     */
    public final void initGrid(Grids_AbstractGrid g) {
        this.setGrid(g);
    }

    /**
     * Initialises _ChunkID.
     *
     * @param chunkID
     */
    public void initChunkID(Grids_2D_ID_int chunkID) {
        ChunkID = chunkID;
    }

    /**
     * Returns a copy of this._ChunkID.
     *
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public Grids_2D_ID_int getChunkID(boolean handleOutOfMemoryError) {
        try {
            Grids_2D_ID_int result = getChunkID();
            ge.tryToEnsureThereIsEnoughMemoryToContinue(handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return getChunkID(handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns a copy of this._ChunkID.
     *
     * @return
     */
    protected Grids_2D_ID_int getChunkID() {
        return new Grids_2D_ID_int(ChunkID);
        //return this._ChunkID;
    }

    /**
     * Returns this.SwapUpToDate
     *
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public boolean isSwapUpToDate(boolean handleOutOfMemoryError) {
        try {
            boolean result = isSwapUpToDate();
            ge.tryToEnsureThereIsEnoughMemoryToContinue(handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return Grids_AbstractGridChunk.this.isSwapUpToDate(handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns this.SwapUpToDate
     *
     * @return
     */
    protected boolean isSwapUpToDate() {
        return this.SwapUpToDate;
    }

    /**
     * Sets this.SwapUpToDate to SwapUpToDate
     *
     * @param swapUpToDate
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     */
    public void setSwapUpToDate(boolean swapUpToDate, boolean handleOutOfMemoryError) {
        try {
            Grids_AbstractGridChunk.this.setSwapUpToDate(swapUpToDate);
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                setSwapUpToDate(swapUpToDate, handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns this.SwapUpToDate
     *
     * @param swapUpToDate
     */
    protected void setSwapUpToDate(boolean swapUpToDate) {
        this.SwapUpToDate = swapUpToDate;
    }

    /**
     * For returning a description of this.
     *
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public String toString(boolean handleOutOfMemoryError) {
        try {
            String result = getDescription();
            ge.tryToEnsureThereIsEnoughMemoryToContinue(handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return toString(handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * For returning a description of this.
     *
     * @return
     */
    protected String getDescription() {
        return getName() + "(ChunkID(" + ChunkID.toString() + "))";
    }

    /**
     * Returns the name of this.
     *
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public String getName(boolean handleOutOfMemoryError) {
        try {
            String result = getName();
            ge.tryToEnsureThereIsEnoughMemoryToContinue(handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return getName(handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns the name of this.
     *
     * @return
     */
    protected String getName() {
        return this.getClass().getName();
    }

    /**
     * Returns an iterator over the cell values. These are not guaranteed to be
     * in any particular order.
     *
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public Grids_AbstractIterator iterator(boolean handleOutOfMemoryError) {
        try {
            Grids_AbstractIterator result = iterator();
            ge.tryToEnsureThereIsEnoughMemoryToContinue(handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return iterator(handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns an iterator over the cell values. These are not guaranteed to be
     * in any particular order.
     *
     * @return
     */
    protected abstract Grids_AbstractIterator iterator();

    /**
     * Returns true if the cell given by chunk cell row index chunkCellRowIndex,
     * chunk cell col index chunkCellColIndex is in this.
     *
     * @param chunkCellRowIndex
     * @param chunkCellColIndex
     * @param handleOutOfMemoryError If true then OutOfMemoryErrors are caught,
     * swap operations are initiated, then the method is re-called. If false
     * then OutOfMemoryErrors are caught and thrown.
     * @return
     */
    public boolean inChunk(int chunkCellRowIndex, int chunkCellColIndex, boolean handleOutOfMemoryError) {
        try {
            boolean result = inChunk(chunkCellRowIndex, chunkCellColIndex);
            ge.tryToEnsureThereIsEnoughMemoryToContinue(handleOutOfMemoryError);
            return result;
        } catch (OutOfMemoryError e) {
            if (handleOutOfMemoryError) {
                ge.clearMemoryReserve();
                if (ge.swapChunkExcept_Account(Grid, ChunkID, handleOutOfMemoryError) < 1L) {
                    throw e;
                }
                ge.initMemoryReserve(Grid, ChunkID, handleOutOfMemoryError);
                return inChunk(chunkCellRowIndex, chunkCellColIndex, handleOutOfMemoryError);
            } else {
                throw e;
            }
        }
    }

    /**
     * Returns true if the cell given by chunk cell row index chunkCellRowIndex,
     * chunk cell col index chunkCellColIndex is in this.
     *
     * @param chunkCellRowIndex
     * @param chunkCellColIndex
     * @return
     */
    protected boolean inChunk(int chunkCellRowIndex, int chunkCellColIndex) {
        int chunkNrows = Grid.getChunkNRows(ChunkID, false);
        int chunkNcols = Grid.getChunkNCols(ChunkID, false);
        return chunkCellRowIndex > -1 && chunkCellRowIndex < chunkNrows && chunkCellColIndex > -1 && chunkCellColIndex < chunkNcols;
    }

    /**
     * For clearing the data associated with this.
     */
    protected abstract void clearData();

    /**
     * For initialising the data associated with this.
     */
    protected abstract void initData();

    /**
     * @param grid the Grid to set
     */
    public void setGrid(Grids_AbstractGrid grid) {
        this.Grid = grid;
    }

}