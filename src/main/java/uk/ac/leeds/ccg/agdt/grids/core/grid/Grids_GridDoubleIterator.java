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
package uk.ac.leeds.ccg.agdt.grids.core.grid;

import uk.ac.leeds.ccg.agdt.grids.core.Grids_2D_ID_int;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_AbstractGridChunk;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_GridChunkDoubleArrayOrMapIterator;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_GridChunkDoubleMap;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_GridChunkDoubleArray;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_AbstractGridChunkDouble;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_AbstractGridChunkRowMajorOrderIterator;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_GridChunkDouble;
import uk.ac.leeds.ccg.agdt.grids.core.grid.chunk.Grids_GridChunkDoubleIterator;

/**
 * For iterating through the values in a Grids_GridDouble. The values are
 * returned chunk by chunk in row major order. The values within each chunk are
 * also returned in row major order.
*
 * @author Andy Turner
 * @version 1.0.0
 */
public class Grids_GridDoubleIterator
        extends Grids_AbstractGridIterator {

    protected Grids_GridDoubleIterator() {
    }

    /**
     * @param g The Grids_GridDouble to iterate over.
     */
    public Grids_GridDoubleIterator(
            Grids_GridDouble g) {
        super(g);
        GridIterator = g.chunkIDChunkMap.keySet().iterator();
        if (GridIterator.hasNext()) {
            ChunkID = (Grids_2D_ID_int) GridIterator.next();
            Chunk = (Grids_AbstractGridChunkDouble) g.chunkIDChunkMap.get(ChunkID);
            if (Chunk == null) {
                Grid.loadIntoCacheChunk(ChunkID);
                Chunk = (Grids_AbstractGridChunkDouble) g.chunkIDChunkMap.get(ChunkID);
            }
            initChunkIterator();
        }
    }

    /**
     * Initialises ChunkIterator.
     */
    @Override
    protected final void initChunkIterator() {
        if (Chunk instanceof Grids_GridChunkDoubleArray) {
            ChunkIterator = new Grids_GridChunkDoubleArrayOrMapIterator(
                    (Grids_GridChunkDoubleArray) Chunk);
        } else if (Chunk instanceof Grids_GridChunkDoubleMap) {
            ChunkIterator = new Grids_GridChunkDoubleArrayOrMapIterator(
                    (Grids_GridChunkDoubleMap) Chunk);
        } else {
            ChunkIterator = new Grids_GridChunkDoubleIterator(
                    (Grids_GridChunkDouble) Chunk);
        }
    }

    /**
     * @param chunk
     * @return Grids_AbstractIterator to iterate over values in chunk.
     */
    @Override
    public Grids_AbstractGridChunkRowMajorOrderIterator getChunkIterator(
            Grids_AbstractGridChunk chunk) {
        if (chunk instanceof Grids_GridChunkDoubleArray) {
            return new Grids_GridChunkDoubleArrayOrMapIterator(
                    (Grids_GridChunkDoubleArray) chunk);
        } else if (chunk instanceof Grids_GridChunkDoubleMap) {
            return new Grids_GridChunkDoubleArrayOrMapIterator(
                    (Grids_GridChunkDoubleMap) chunk);
        } else if (chunk instanceof Grids_GridChunkDouble) {
            return new Grids_GridChunkDoubleIterator( 
                    (Grids_GridChunkDouble) chunk);
        } else {
            throw new Error("Unrecognised type of chunk "
                        + this.getClass().getName()
                        + ".getChunkIterator(Chunk(" + chunk.toString() + "))");
        }
    }

    @Override
    public Grids_GridDouble getGrid() {
        return (Grids_GridDouble) Grid;
    }
}