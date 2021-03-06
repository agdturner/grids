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
package uk.ac.leeds.ccg.grids.d2.grid.bd;

import uk.ac.leeds.ccg.grids.d2.chunk.bd.Grids_ChunkFactoryBD;
import java.io.IOException;
import java.math.BigDecimal;
import uk.ac.leeds.ccg.generic.io.Generic_Path;
import uk.ac.leeds.ccg.grids.d2.grid.Grids_Dimensions;
import uk.ac.leeds.ccg.grids.core.Grids_Environment;
import uk.ac.leeds.ccg.grids.d2.chunk.bd.Grids_ChunkFactoryBDSinglet;
import uk.ac.leeds.ccg.grids.d2.grid.Grids_Grid;
import uk.ac.leeds.ccg.grids.d2.grid.Grids_GridFactory;
import uk.ac.leeds.ccg.grids.d2.stats.Grids_StatsBD;
import uk.ac.leeds.ccg.grids.d2.stats.Grids_StatsNotUpdatedBD;
import uk.ac.leeds.ccg.generic.io.Generic_FileStore;

/**
 * A factory for constructing Grids_GridBD instances.
 *
 * @author Andy Turner
 * @version 1.0.0
 */
public class Grids_GridFactoryBD extends Grids_GridFactory {

    private static final long serialVersionUID = 1L;

    /**
     * The noDataValue for creating chunks.
     */
    protected BigDecimal noDataValue;

    public Grids_ChunkFactoryBDSinglet gridChunkBDFactory;
//    public Grids_GridChunkBDMapFactory ChunkBDMapFactory;
//    public Grids_GridChunkBDArrayFactory ChunkBDArrayFactory;
    public Grids_ChunkFactoryBD defaultGridChunkBDFactory;

    public Grids_StatsBD stats;

    /**
     * Creates a new Grids_GridBDFactory. {@link #dim} is set to {@code null};
     * {@link #stats} is set to {@code  new Grids_StatsNotUpdatedBD(e)}.
     *
     *
     * @param e What {@link #env} is set to.
     * @param fs What {@link #store} is set to.
     * @param gcdf What {@link #gridChunkBDFactory} is set to.
     * @param dgcdf What {@link #defaultGridChunkBDFactory} is set to.
     * @param cnr What {@link #chunkNRows} is set to.
     * @param cnc What {@link #chunkNCols} is set to.
     */
    public Grids_GridFactoryBD(Grids_Environment e, Generic_FileStore fs,
            Grids_ChunkFactoryBDSinglet gcdf,
            Grids_ChunkFactoryBD dgcdf, int cnr, int cnc) {
        this(e, fs, gcdf, dgcdf, BigDecimal.valueOf(-Double.MAX_VALUE), cnr, cnc,
                null, new Grids_StatsNotUpdatedBD(e));
    }

    /**
     * Creates a new Grids_GridBDFactory.
     *
     * @param ge What {@link #env} is set to.
     * @param fs What {@link #store} is set to.
     * @param gcdf What {@link #gridChunkBDFactory} is set to.
     * @param dgcdf What {@link #defaultGridChunkBDFactory} is set to.
     * @param ndv What {@link #noDataValue} is set to.
     * @param chunkNRows What {@link #chunkNRows} is set to.
     * @param chunkNCols What {@link #chunkNCols} is set to.
     * @param dim What {@link #dim} is set to.
     * @param stats What {@link #stats} is set to.
     */
    public Grids_GridFactoryBD(Grids_Environment ge, Generic_FileStore fs,
            Grids_ChunkFactoryBDSinglet gcdf,
            Grids_ChunkFactoryBD dgcdf, BigDecimal ndv, int chunkNRows,
            int chunkNCols, Grids_Dimensions dim, Grids_StatsBD stats) {
        super(ge, fs, chunkNRows, chunkNCols, dim);
        gridChunkBDFactory = gcdf;
        defaultGridChunkBDFactory = dgcdf;
        this.stats = stats;
        this.noDataValue = ndv;
    }

    /**
     * For setting {@link #defaultGridChunkBDFactory}.
     *
     * @param cf What {@link #defaultGridChunkBDFactory} is set to.
     */
    public void setDefaultChunkFactory(Grids_ChunkFactoryBD cf) {
        defaultGridChunkBDFactory = cf;
    }

    /**
     * @return {@link #noDataValue}
     */
    public BigDecimal getNoDataValue() {
        return noDataValue;
    }

    /**
     * Sets {@link #noDataValue}.
     *
     * @param ndv What {@link #noDataValue} is set to.
     */
    public void setNoDataValue(BigDecimal ndv) {
        this.noDataValue = ndv;
    }

    /**
     * Creates a new Grids_GridBD with all values set to
     * {@link #noDataValue} and with {@link #stats} that are not updated and
     * with chunks made using {@link #gridChunkBDFactory}.
     *
     * @param nRows The number of rows in the grid.
     * @param nCols The number of columns in the grid.
     * @param dimensions The dimensions (xmin, ymin, xmax, ymax, cellsize) of
     * the grid to be created.
     * @return A new Grids_GridBD with all values set to
     * {@link #noDataValue} and with {@link #stats} that are not updated and
     * with chunks made using {@link #gridChunkBDFactory}.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    @Override
    public Grids_GridBD create(long nRows, long nCols,
            Grids_Dimensions dimensions) throws IOException,
            ClassNotFoundException, Exception {
        return create(new Grids_StatsNotUpdatedBD(env),
                gridChunkBDFactory, nRows, nCols, dimensions);
    }

    /**
     * Creates a new Grids_GridBD with all values set to
     * {@link #noDataValue}.
     *
     * @param stats The type of Grids_StatsBD to accompany the returned
     * grid.
     * @param cf The Grids_ChunkFactoryBD for creating chunks that the
     * constructed Grid is to be made of.
     * @param nRows The number of rows in the grid.
     * @param nCols The number of columns in the grid.
     * @param dimensions The xmin, ymin, xmax, ymax, cellsize.
     * @return A new Grids_GridBD with all values set to
     * {@link #noDataValue}.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    public Grids_GridBD create(Grids_StatsBD stats,
            Grids_ChunkFactoryBD cf, long nRows, long nCols,
            Grids_Dimensions dimensions) throws IOException,
            ClassNotFoundException, Exception {
        Grids_GridBD r = new Grids_GridBD(getStats(stats), store,
                store.getNextID(), cf, chunkNRows,
                chunkNCols, nRows, nCols, dimensions, noDataValue, env);
        store.addDir();
        return r;
    }

    /**
     * Creates a new Grids_GridBD with values set from {@code #g}. The stats
     * for the grid are not updated and the
     * {@link #defaultGridChunkBDFactory} is used to create chunks.
     *
     * @param g The grid used to set the values of the grid created.
     * @param startRow The start row index of {@code #g}.
     * @param startCol The start column index of {@code #g}.
     * @param endRow The end row index of {@code #g}.
     * @param endCol The end column index of {@code #g}.
     * @return A new Grids_GridBD with all values set from {@code #g}.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    @Override
    public Grids_GridBD create(Grids_Grid g,
            long startRow, long startCol, long endRow, long endCol)
            throws IOException, ClassNotFoundException, Exception {
        return create(new Grids_StatsNotUpdatedBD(env), g,
                defaultGridChunkBDFactory, startRow, startCol, endRow,
                endCol);
    }

    /**
     * Creates a new Grids_GridBD with values set from {@code #g}.
     *
     * @param stats The type of Grids_StatsBD to accompany the created grid.
     * @param g The grid used to set the values of the grid created.
     * @param cf The chunk factory for creating chunks.
     * @param startRow The start row index of {@code #g}.
     * @param startCol The start column index of {@code #g}.
     * @param endRow The end row index of {@code #g}.
     * @param endCol The end column index of {@code #g}.
     * @return A new Grids_GridBD with all values set from {@code #g}.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    public Grids_GridBD create(Grids_StatsBD stats,
            Grids_Grid g, Grids_ChunkFactoryBD cf,
            long startRow, long startCol, long endRow, long endCol)
            throws IOException, ClassNotFoundException, Exception {
        Grids_GridBD r = new Grids_GridBD(getStats(stats), store,
                store.getNextID(), g, cf, chunkNRows,
                chunkNCols, startRow, startCol, endRow, endCol, noDataValue);
        store.addDir();
        return r;
    }

    /**
     * @param gridFile Either a directory, or a formatted File with a specific
     * extension containing the data and information about the grid to be
     * constructed.
     * @param startRow The topmost row index of the grid stored as gridFile.
     * @param startCol The leftmost column index of the grid stored as gridFile.
     * @param endRow The bottom row index of the grid stored as gridFile.
     * @param endCol The rightmost column index of the grid stored as gridFile.
     * @return A new Grids_GridBD with values obtained from gridFile.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    @Override
    public Grids_GridBD create(Generic_Path gridFile, long startRow,
            long startCol, long endRow, long endCol) throws IOException,
            ClassNotFoundException, Exception {
        return create(new Grids_StatsNotUpdatedBD(env),
                gridFile, defaultGridChunkBDFactory, startRow, startCol,
                endRow, endCol);
    }

    /**
     * @param stats The type of Grids_StatsBD to accompany the returned
     * grid.
     * @param gridFile Either a directory, or a formatted File with a specific
     * extension containing the data and information about the grid to be
     * constructed.
     * @param cf The preferred factory for creating chunks that the constructed
     * Grid is to be made of.
     * @param startRow The topmost row index of the grid stored as gridFile.
     * @param startCol The leftmost column index of the grid stored as gridFile.
     * @param endRow The bottom row index of the grid stored as gridFile.
     * @param endCol The rightmost column index of the grid stored as gridFile.
     * @return A new Grids_GridBD with values obtained from gridFile.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    public Grids_GridBD create(Grids_StatsBD stats,
            Generic_Path gridFile, Grids_ChunkFactoryBD cf,
            long startRow, long startCol, long endRow, long endCol)
            throws IOException, ClassNotFoundException, Exception {
        Grids_GridBD r = new Grids_GridBD(getStats(stats), store,
                store.getNextID(), gridFile, cf, chunkNRows, chunkNCols,
                startRow, startCol, endRow, endCol, noDataValue, env);
        store.addDir();
        return r;
    }

    /**
     * @param gridFile Either a directory, or a formatted File with a specific
     * extension containing the data and information about the grid to be
     * returned.
     * @return A new Grids_GridBD with values obtained from gridFile.
     * @throws java.io.IOException If encountered.
     * @throws java.lang.ClassNotFoundException If encountered.
     */
    @Override
    public Grids_GridBD create(Generic_Path gridFile)
            throws IOException, ClassNotFoundException, Exception {
        Grids_GridBD r = new Grids_GridBD(env, store, store.getNextID(),
                gridFile, noDataValue);
        store.addDir();
        return r;
    }

    /**
     * For duplicating stats.
     * 
     * @param stats What is to be duplicated.
     * @return A new Grids_StatsBD of the same type for use.
     */
    private Grids_StatsBD getStats(Grids_StatsBD stats) {
        if (stats instanceof Grids_StatsNotUpdatedBD) {
            return new Grids_StatsNotUpdatedBD(env);
        } else {
            return new Grids_StatsBD(env);
        }
    }
}
