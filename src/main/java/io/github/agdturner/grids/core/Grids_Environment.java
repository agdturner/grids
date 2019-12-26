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
package io.github.agdturner.grids.core;

import io.github.agdturner.grids.d2.chunk.Grids_Chunk;
import io.github.agdturner.grids.d2.grid.Grids_Grid;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import uk.ac.leeds.ccg.agdt.generic.core.Generic_Environment;
import uk.ac.leeds.ccg.agdt.generic.io.Generic_Defaults;
import uk.ac.leeds.ccg.agdt.generic.io.Generic_Path;
import uk.ac.leeds.ccg.agdt.math.Math_BigDecimal;
import io.github.agdturner.grids.io.Grids_Files;
import io.github.agdturner.grids.process.Grids_Processor;

/**
 * Grids_Environment.
 *
 * @author Andy Turner
 * @version 1.0.0
 */
public class Grids_Environment extends Grids_MemoryManager
        implements Grids_Memory {

    private static final long serialVersionUID = 1L;

    /**
     * A set of all the grids. These may have chunks stored in the fast access
     * memory that can be cached (stored) elsewhere and cleared from the fast
     * access memory (swapped out). If a cache of the chunk already exists and
     * is effectively no different to the copy of the chunk in the fast access
     * memory, then the chunk can be cleared from the fast access memory by
     * setting the reference to this to null. The chunk may not be immediately
     * cleared, the timing of this is left up to normal garbage collection.
     *
     * If chunks are not in the fast access memory when they are wanted, they
     * can be loaded from a cache as needed - although this may necessitate
     * other data being swapped out in order to manage with the available fast
     * access memory.
     */
    protected transient Set<Grids_Grid> grids;

    /**
     * For indicating which chunks of which grids are not to be cleared from the
     * fast access memory. This map is modified by data processing algorithms to
     * to try to ensure a reasonable efficiency and prevent chunks being cleared
     * only to be reloaded shortly afterwards, when there were either: other
     * chunks to clear that would not have been reloaded in the same frame; or,
     * some other data from some other environment that would have been better
     * to clear instead.
     *
     * Maintaining this map has a marginal efficiency cost which may be
     * noticeable when all the data fits easily into the available fast access
     * memory and comparing the processing functionality of this library with
     * other libraries which essentially generate the same processed results.
     * However this library is more geared to supporting the processing of large
     * volumes of data (when it is typically not the case that all the data will
     * fit in the fast access memory of the machine, and processing may involve
     * a significant amount of caching, clearing and reloading of data). In such
     * circumstances it would be more interesting to compare the performance of
     * this library with other software that is also geared are not geared for
     * such memory management.
     *
     * For some methods cell values from a set of neighbouring chunks are wanted
     * along with other nearby cell values or chunk statistics from other grids.
     * An example case is calculating geographically weighted statistics such as
     * the difference between two grids, where the statistic takes in values
     * from each grid within a specific distance of any cell to produce a result
     * for a given cell in a new output grid.
     *
     * There are additional maps and sets that help improve algorithmic
     * efficiency further, including a set of very lightweight chunks which are
     * hardly worth clearing unless options are very limited.
     *
     * In some cases it might be best for the method to throw an Exception
     * rather than to slowly grind on and potentially taking overly long to
     * produce a result when restructuring the data into smaller or larger
     * chunks or storing the data in chunks of different types may be a
     * worthwhile step.
     *
     * An overhead is involved in changing chunk types and chunk restructuring.
     * What is best to do depends on many factors, but generally what is wanted
     * is a good solution or a solution that works rather than the best solution
     * - the one that creates a result in the fastest and most efficient way.
     */
    protected transient HashMap<Grids_Grid, Set<Grids_2D_ID_int>> notToClear;

    /**
     * For storing a {@link Math_BigDecimal} instance.
     */
    public transient Math_BigDecimal bd;

    /**
     * For storing a {@link Grids_Processor} instance.
     */
    protected transient Grids_Processor processor;

    /**
     * For storing a {@link Grids_Files} instance.
     */
    public transient Grids_Files files;

    /**
     * For storing a {@link Generic_Environment} instance.
     */
    public transient final Generic_Environment env;

    /**
     * Creates a new Grids_Environment.
     *
     * @see #Grids_Environment(Generic_Environment). The Generic_Environment is
     * initialised using:
     * {@code new Generic_Environment(new Generic_Defaults())}.
     *
     * @throws java.io.IOException If encountered.
     * @throws Exception If there is another problem setting up the file store.
     */
    public Grids_Environment() throws IOException, Exception {
        this(new Generic_Environment(new Generic_Defaults()));
    }

    /**
     * Creates a new Grids_Environment.
     *
     * @see #Grids_Environment(Generic_Environment, Generic_Path). The
     * Generic_Path is obtained from {@code e}.
     *
     * @param e The Generic_Environment.
     * @throws java.io.IOException If encountered.
     */
    public Grids_Environment(Generic_Environment e) throws IOException,
            Exception {
        this(e, e.files.getDir());
    }

    /**
     * Creates a new Grids_Environment.
     *
     * @param e What {@link #env} is set to.
     * @param dir Used to initialise {@link #files} using
     * {@link Grids_Files(Generic_Path)}.
     * @throws java.io.IOException If encountered.
     */
    public Grids_Environment(Generic_Environment e, Generic_Path dir)
            throws IOException, Exception {
        this.env = e;
        initMemoryReserve(Default_Memory_Threshold, e);
        initGrids();
        initNotToClear();
        Path p = e.getLogDir(Grids_Strings.s_grids);
        files = new Grids_Files(new Generic_Defaults(Paths.get(dir.toString(),
                Grids_Strings.s_grids)));
        e.files.setDir(p);
    }

    /**
     * If {@link #processor} is not {@code null}, it is returned.If
     * {@link #processor} is {@code null} it is initialised and then returned.
     *
     * @return {@link #processor} initialising first if it is {@code null}.
     * @throws java.io.IOException If encountered initialising
     * {@link #processor}.
     * @throws java.lang.ClassNotFoundException If encountered.
     * @throws java.lang.Exception If encountered.
     */
    public Grids_Processor getProcessor() throws IOException,
            ClassNotFoundException, Exception {
        if (processor == null) {
            processor = new Grids_Processor(this);
        }
        return processor;
    }

    /**
     * If {@link #grids} is {@code null} it is initialised as a new
     * {@link java.util.Set}.
     */
    protected final void initGrids() {
        if (grids == null) {
            grids = new HashSet<>();
        }
    }

    /**
     * Initialise or re-initialise a store of references to data that would
     * ideally not be cached.
     */
    public final void initNotToClear() {
        notToClear = new HashMap<>();
    }

    /**
     * Adds the chunk IDs of {@code g} to {@link #notToClear}.
     *
     * @param g The grid to add to {@link #notToClear}.
     */
    public final void addToNotToClear(Grids_Grid g) {
        notToClear.put(g, g.getChunkIDs());
    }

    /**
     * Removes {@code g} from the {@link #notToClear}.
     *
     * @param g The grid to remove from {@link #notToClear}.
     */
    public final void removeFromNotToClear(Grids_Grid g) {
        notToClear.remove(g);
    }

    /**
     * Adds chunk row {@code chunkRow} of chunks of {@code g} to
     * {@link #notToClear}.
     *
     * @param g The grid with chunks to add to {@link #notToClear}.
     * @param cr The row of chunks in {@code g} to add to {@link #notToClear}.
     */
    public final void addToNotToClear(Grids_Grid g, int cr) {
        int n = g.getNChunkCols();
        for (int c = 0; c < n; c++) {
            addToNotToClear(g, new Grids_2D_ID_int(cr, c));
        }
    }

    /**
     * Removed chunk row {@code chunkRow} of chunks of {@code g} from
     * {@link #notToClear}.
     *
     * @param g The grid with chunks to remove from {@link #notToClear}.
     * @param cr The row of chunks in {@code g} to remove from
     * {@link #notToClear}.
     */
    public final void removeFromNotToClear(Grids_Grid g, int cr) {
        int n = g.getNChunkCols();
        for (int c = 0; c < n; c++) {
            removeFromNotToClear(g, new Grids_2D_ID_int(cr, c));
        }
    }

    /**
     * Adds the chunk ID {@code i} of {@code g} to {@link #notToClear}.
     *
     * @param g The grid containing chunk with chunk ID {@code i}.
     * @param i The chunk ID to add to {@link #notToClear}.
     */
    public final void addToNotToClear(Grids_Grid g, Grids_2D_ID_int i) {
        if (notToClear.containsKey(g)) {
            notToClear.get(g).add(i);
        } else {
            Set<Grids_2D_ID_int> chunkIDs = new HashSet<>();
            chunkIDs.add(i);
            notToClear.put(g, chunkIDs);
        }
    }

    /**
     * Adds the chunk ID {@code i} of each grid in {@code g} to
     * {@link #notToClear}.
     *
     * @param g The array of grids for which each chunk ID {@code i} is added to
     * {@link #notToClear}.
     * @param i The chunk ID to add to {@link #notToClear}.
     */
    public final void addToNotToClear(Grids_Grid[] g, Grids_2D_ID_int i) {
        for (Grids_Grid g1 : g) {
            addToNotToClear(g1, i);
        }
    }

    /**
     * Puts everything in {@code m} into {@link notToClear}.
     *
     * @param m The map contents to add to {@link #notToClear}.
     */
    public final void addToNotToClear(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) {
        Iterator<Grids_Grid> ite = m.keySet().iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            if (notToClear.containsKey(g)) {
                notToClear.get(g).addAll(m.get(g));
            } else {
                notToClear.put(g, m.get(g));
            }
        }
    }

    /**
     * Removes the chunk ID {@code i} of each grid in {@code g} from
     * {@link #notToClear}.
     *
     * @param g The array of grids for which each chunk ID {@code i} is removed
     * from {@link #notToClear}.
     * @param i The chunk ID to remove from {@link #notToClear}.
     */
    public final void removeFromNotToClear(Grids_Grid[] g, Grids_2D_ID_int i) {
        for (Grids_Grid g1 : g) {
            removeFromNotToClear(g1, i);
        }
    }

    /**
     * Removes everything in {@code m} from {@link notToClear}.
     *
     * @param m The map contents to remove from {@link #notToClear}.
     */
    public final void removeFromNotToClear(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) {
        Iterator<Grids_Grid> ite = m.keySet().iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            if (notToClear.containsKey(g)) {
                notToClear.get(g).removeAll(m.get(g));
            }
        }
    }

    /**
     * Adds all the chunk IDs in {@code chunkIDs} of chunks in {@code g} into
     * {@link #notToClear}.
     *
     * @param g The grid with chunk IDs to add to {@link #notToClear}.
     * @param chunkIDs The chunk IDs to add to {@link #notToClear}.
     */
    public final void addToNotToClear(Grids_Grid g,
            Set<Grids_2D_ID_int> chunkIDs) {
        if (notToClear.containsKey(g)) {
            notToClear.get(g).addAll(chunkIDs);
        } else {
            notToClear.put(g, chunkIDs);
        }
    }

    /**
     * Remove the chunk ID {@code i} from {@code g} from {@link #notToClear}.
     *
     * @param g The grid with chunk ID {@code i} which will be removed from
     * {@link #notToClear}.
     * @param i The chunk ID of {@code g} to remove from {@link #notToClear}.
     */
    public final void removeFromNotToClear(Grids_Grid g, Grids_2D_ID_int i) {
        if (notToClear.containsKey(g)) {
            /**
             * Decided that it is best not to remove g from notToClear if
             * notToClear.get(g).isEmpty(). So the empty Set remains and this
             * takes up a small amount of resource, but it is probably better to
             * keep it in case it is re-used rather than destroying it.
             */
            notToClear.get(g).remove(i);
//            Set<Grids_2D_ID_int> chunkIDs = notToClear.get(g);
//            chunkIDs.remove(chunkID);
//            if (chunkIDs.isEmpty()) {
//                notToClear.remove(g);
//            }
        }
    }

    /**
     * Adds all the ChunkIDs of g that are within cellDistance of the chunk with
     * chunk ID {@code i} to {@link #notToClear}.
     *
     * @param g The Grid.
     * @param i Central chunk ID.
     * @param chunkRow The chunk row index of chunk with chunk ID {@code i} -
     * provided for convenience.
     * @param chunkCol The chunk column index of chunk with chunk ID {@code i} -
     * provided for convenience.
     * @param chunkNRows The normal number of rows in a chunk. (The last row may
     * have fewer.)
     * @param chunkNCols The normal number of columns in a chunk. (The last
     * column may have fewer.)
     * @param cellDistance The cell distance within which all chunk IDs in
     * {@code g} are added to {@link #notToClear}.
     */
    public final void addToNotToClear(Grids_Grid g, Grids_2D_ID_int i,
            int chunkRow, int chunkCol, int chunkNRows, int chunkNCols,
            int cellDistance) {
        Set<Grids_2D_ID_int> chunkIDs;
        if (notToClear.containsKey(g)) {
            chunkIDs = notToClear.get(g);
        } else {
            chunkIDs = new HashSet<>();
            notToClear.put(g, chunkIDs);
        }
        int t;
        int r = 0;
        t = 0;
        while (t < cellDistance) {
            t += chunkNRows;
            r++;
        }
        int j = 0;
        t = 0;
        while (t < cellDistance) {
            t += chunkNCols;
            j++;
        }
        for (int k = -r; k <= r; k++) {
            int cr = chunkRow + k;
            for (int l = -j; l <= j; l++) {
                int cc = chunkCol + l;
                if (g.isInGrid(cr, cc)) {
                    chunkIDs.add(new Grids_2D_ID_int(cr, cc));
                }
            }
        }
    }

    /**
     * Initialises grids.
     *
     * @param grids
     */
    protected void initGrids(Set<Grids_Grid> grids) {
        if (this.grids == null) {
            this.grids = grids;
        } else {
            if (grids == null) {
                this.grids = new HashSet<>();
            } else {
                this.grids = grids;
            }
        }
    }

    /**
     * @return the grids
     */
    public Set<Grids_Grid> getGrids() {
        return grids;
    }

    /**
     * Adds {@code g} to {@link #grids}.
     *
     * @param g The grid to add to {@link #grids}.
     */
    public void addGrid(Grids_Grid g) {
        grids.add(g);
    }

    /**
     * Remove {@code g} from {@link #grids}.
     *
     * @param g The grid to remove from {@link #grids}.
     */
    public void removeGrid(Grids_Grid g) {
        grids.remove(g);
    }

    /**
     * Initialises grids and memory reserve.
     *
     * @param grids What {@link #grids} is set to.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it.
     * @throws java.io.IOException If encountered.
     */
    public void initGridsAndMemoryReserve(Set<Grids_Grid> grids, boolean hoome)
            throws IOException, Exception {
        try {
            initGridsAndMemoryReserve(grids);
            checkAndMaybeFreeMemory(hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!swapChunk_Account().success) {
                    throw e;
                }
                initGridsAndMemoryReserve(grids, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #grids} and sets the memory reserve in each to
     * {@link #MemoryReserve}.
     *
     * @param grids What {@link #grids} is set to.
     */
    protected void initGridsAndMemoryReserve(Set<Grids_Grid> grids) {
        initGrids(grids);
        Iterator<Grids_Grid> ite = this.grids.iterator();
        if (ite.hasNext()) {
            ite.next().env.setMemoryReserve(MemoryReserve);
        } else {
            initMemoryReserve(env);
        }
    }

    /**
     * Initialises {@link #MemoryReserve}. A detailed account of any clearing is
     * returned.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return A detailed account of any clearing.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_AccountDetail initMemoryReserve_AccountDetail(boolean hoome) throws IOException,
            Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_AccountDetail(hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                Grids_AccountDetail r = swapChunk_AccountDetail();
                r.add(checkAndMaybeFreeMemory_AccountDetail(hoome));
                r.add(initMemoryReserve_AccountDetail(hoome));
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve}. An account of how many chunks were
     * cleared in the process is returned.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return The number of chunks cleared in the process of initialising
     * {@link #MemoryReserve}.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_Account initMemoryReserve_Account(boolean hoome) throws IOException,
            Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_Account(hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                Grids_Account r = swapChunk_Account();
                if (!r.success) {
                    throw e;
                }
                r.add(checkAndMaybeFreeMemory_Account(hoome));
                r.add(initMemoryReserve_Account(hoome));
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than any chunks in {@code g}) or other data. No account of
     * any clearing is returned.
     *
     * @param g A grid with chunks not to be swapped out in the event of any
     * memory handling.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public final void initMemoryReserve(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            initMemoryReserve(env);
            checkAndMaybeFreeMemory(g, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (swapChunkExcept_Account(g) < 1L) {
                    throw e;
                }
                checkAndMaybeFreeMemory(g, hoome);
                initMemoryReserve(g, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than the chunk with ID {@code i}) or other data. No account
     * of any clearing is returned.
     *
     * @param i The ID of a chunk not to cleared in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public final void initMemoryReserve(Grids_2D_ID_int i, boolean hoome)
            throws IOException, Exception {
        try {
            initMemoryReserve(env);
            checkAndMaybeFreeMemory(i, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (swapChunkExcept_Account(i) < 1L) {
                    throw e;
                }
                checkAndMaybeFreeMemory(i, hoome);
                initMemoryReserve(i, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than the chunk with ID {@code i}) or other data. A detailed
     * account of any chunks cleared is returned.
     *
     * @param i The ID of a chunk not to cleared in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return A detailed account of any chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_AccountDetail initMemoryReserve_AccountDetail(
            Grids_2D_ID_int i, boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_AccountDetail(i, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                Grids_AccountDetail r = swapChunkExcept_AccountDetail(i);
                if (r.isEmpty()) {
                    throw e;
                }
                r.add(initMemoryReserve_AccountDetail(i, hoome));
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than the chunk with ID {@code i}) or other data. An account
     * of any clearing is returned.
     *
     * @param i The ID of a chunk not to cleared in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return The number of chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_Account initMemoryReserve_Account(Grids_2D_ID_int i, boolean hoome)
            throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_Account(i, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(i);
                if (r < 1L) {
                    throw e;
                }
                r += initMemoryReserve_Account(i, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than the chunk with ID {@code i} in grid {@code g}) or
     * other data. No account of any clearing is returned.
     *
     * @param g The grid in which chunk with ID {@code i} is not cleared.
     * @param i The ID of a chunk not to cleared in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public final void initMemoryReserve(Grids_Grid g, Grids_2D_ID_int i,
            boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            checkAndMaybeFreeMemory(g, i, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (swapChunkExcept_Account(g, i) < 1L) {
                    throw e;
                }
                checkAndMaybeFreeMemory(g, i, hoome);
                initMemoryReserve(g, i, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than the chunk with ID {@code i} in grid {@code g}) or
     * other data. An account of any clearing is returned.
     *
     * @param g The grid in which chunk with ID {@code i} is not cleared.
     * @param i The ID of a chunk not to cleared in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return The number of chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_Account initMemoryReserve_Account(Grids_Grid g, Grids_2D_ID_int i,
            boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_Account(g, i, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g, i);
                if (r < 1L) {
                    throw e;
                }
                r += checkAndMaybeFreeMemory_Account(g, i, hoome);
                r += initMemoryReserve_Account(g, i, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than the chunk with ID {@code i} in grid {@code g}) or
     * other data. A detailed account of any clearing is returned.
     *
     * @param g The grid in which chunk with ID {@code i} is not cleared.
     * @param i The ID of a chunk not to cleared in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return A detailed account of any chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_AccountDetail initMemoryReserve_AccountDetail(Grids_Grid g, Grids_2D_ID_int i,
                    boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_AccountDetail(g, i, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(g, i);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = checkAndMaybeFreeMemory_AccountDetail(g, i, hoome);
                combine(r, pr);
                pr = initMemoryReserve_AccountDetail(g, i, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those with chunk IDs in {@code s} in grid {@code g})
     * or other data. A detailed account of any clearing is returned.
     *
     * @param g The grid from which chunks with IDs in {@code s} are not
     * cleared.
     * @param s The set of chunk IDs of a chunk not to cleared from grid (@code
     * g )in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return A detailed account of any chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_AccountDetail initMemoryReserve_AccountDetail(Grids_Grid g,
                    Set<Grids_2D_ID_int> s, boolean hoome) throws IOException,
            Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_AccountDetail(g, s, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(g, s);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = checkAndMaybeFreeMemory_AccountDetail(g, s, hoome);
                combine(r, pr);
                pr = initMemoryReserve_AccountDetail(g, s, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those in grid {@code g}) or other data. A detailed
     * account of any clearing is returned.
     *
     * @param g The grid from which chunks with IDs in {@code s} are not
     * cleared.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return A detailed account of any chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_AccountDetail initMemoryReserve_AccountDetail(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_AccountDetail(g, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(g);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = checkAndMaybeFreeMemory_AccountDetail(g, hoome);
                combine(r, pr);
                pr = initMemoryReserve_AccountDetail(g, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those in {@code m}) or other data. An account of any
     * clearing is returned.
     *
     * @param m A map containing chunk IDs of a chunks not to cleared from grids
     * in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return The number of chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_Account initMemoryReserve_Account(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m, boolean hoome)
            throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_Account(m, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!swapChunkExcept_Account(m).success) {
                    throw e;
                }
                long r = 1;
                r += checkAndMaybeFreeMemory_Account(m, hoome);
                r += initMemoryReserve_Account(m, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those with chunk IDs in {@code s} in grid {@code g})
     * or other data. An account of any clearing is returned.
     *
     * @param g The grid from which chunks are not cleared.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return The number of chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_Account initMemoryReserve_Account(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_Account(g, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g);
                if (r < 1L) {
                    throw e;
                }
                r += checkAndMaybeFreeMemory_Account(g, hoome);
                r += initMemoryReserve_Account(g, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those with chunk IDs in {@code s} in grid {@code g})
     * or other data. An account of any clearing is returned.
     *
     * @param g The grid from which chunks with IDs in {@code s} are not
     * cleared.
     * @param s The set of chunk IDs of a chunk not to cleared from grid (@code
     * g )in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return The number of chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_Account initMemoryReserve_Account(Grids_Grid g, Set<Grids_2D_ID_int> s,
            boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_Account(g, s, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g, s);
                if (r < 1L) {
                    throw e;
                }
                r += checkAndMaybeFreeMemory_Account(g, s, hoome);
                r += initMemoryReserve_Account(g, s, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those with chunk IDs in {@code s} in grid {@code g})
     * or other data. No account of any clearing is returned.
     *
     * @param g The grid from which chunks with IDs in {@code s} are not
     * cleared.
     * @param s The set of chunk IDs of a chunk not to cleared from grid (@code
     * g )in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @throws IOException If encountered.
     */
    @Override
    public final void initMemoryReserve(Grids_Grid g, Set<Grids_2D_ID_int> s,
            boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            checkAndMaybeFreeMemory(g, s, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (swapChunkExcept_Account(g, s) < 1L) {
                    throw e;
                }
                checkAndMaybeFreeMemory(g, s, hoome);
                initMemoryReserve(g, s, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those with chunk IDs in {@code s} in grid {@code g})
     * or other data. No account of any clearing is returned.
     *
     * @param m The map of chunk IDs of a chunks not to cleared in any memory
     * management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public void initMemoryReserve(HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m,
            boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            checkAndMaybeFreeMemory(m, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!swapChunkExcept_Account(m).success) {
                    throw e;
                }
                checkAndMaybeFreeMemory(m, hoome);
                initMemoryReserve(m, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Initialises {@link #MemoryReserve} and checks memory potentially clearing
     * chunks (other than those in {@code m}) or other data. A detailed account
     * of any clearing is returned.
     *
     * @param m A map containing chunk IDs of a chunks not to cleared from grids
     * in any memory management.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory before calling this method again.
     * @return A detailed account of any chunks cleared.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public Grids_AccountDetail initMemoryReserve_AccountDetail(
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m,
                    boolean hoome) throws IOException, Exception {
        try {
            initMemoryReserve(env);
            return checkAndMaybeFreeMemory_AccountDetail(m, hoome);
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(m);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = checkAndMaybeFreeMemory_AccountDetail(m, hoome);
                combine(r, pr);
                pr = initMemoryReserve_AccountDetail(m, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. If available fast access memory is not low then this simply returns
     * true. If available fast access memory is low, then an attempt is made to
     * clear some chunks. Chunks in {@link #notToClear} are not cleared unless
     * desperate. If not enough data is found to clear then an OutOfMemoryError
     * is thrown.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return true if there is sufficient memory to continue and throws an
     * OutOfMemoryError otherwise.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory(boolean hoome) throws IOException,
            Exception {
        try {
            if (checkAndMaybeFreeMemory()) {
                return true;
            } else {
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError("No more fast access memory that "
                        + "grids is using is avaialble to clear, try clearing "
                        + "data from other environments.");
            }
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!checkAndMaybeFreeMemory()) {
                    throw e;
                }
                initMemoryReserve(env);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by clearing chunks
     * from memory. If available fast access memory is not low then this simply
     * returns {@code true}. If available fast access memory is low, then an
     * attempt is made to cache some chunks. Chunks in {@link NotToClear} are
     * not cleared unless desperate.
     *
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory() throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            if (notToClear.isEmpty()) {
                return checkAndMaybeFreeMemory_CacheAny();
            } else {
                do {
                    if (swapChunkExcept_Account(notToClear).success) {
                        if (getTotalFreeMemory() < Memory_Threshold) {
                            return true;
                        }
                    } else {
                        break;
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
                return checkAndMaybeFreeMemory_CacheAny();
            }
        }
        return false;
    }

    /**
     * Check and maybe free fast access memory by writing chunks to file. If
     * available fast access memory is not low then this simply returns true. If
     * available fast access memory is low, then an attempt is made to cache
     * some chunks. Chunks in NotToCache are not cached unless desperate.
     *
     * @return true if there is sufficient memory to continue and false
     * otherwise.
     * @throws java.io.IOException If encountered.
     */
    protected boolean checkAndMaybeFreeMemory_CacheAny() throws IOException,
            Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            do {
                if (!swapChunk_Account().success) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            return getTotalFreeMemory() < Memory_Threshold;
        } else {
            return true;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. If available fast access memory is not low then this simply returns
     * true. If available fast access memory is low, then an attempt is made to
     * cache some chunks. Chunks in NotToCache are not cached unless desperate.
     * No chunk in g is cached. If not enough data is found to cache then an
     * OutOfMemoryError is thrown.
     *
     * @param g The grid from which chunks are not cleared.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return {@code true} if there is sufficient memory to continue and throws
     * an OutOfMemoryError otherwise.
     * @throws java.io.IOException If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            if (!checkAndMaybeFreeMemory(g)) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory(" + g.getClass().getName()
                        + ",boolean)";
//                System.out.println(message);
//                // Set to exit method with OutOfMemoryError
//                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return true;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!checkAndMaybeFreeMemory(g)) {
                    throw e;
                }
                initMemoryReserve(g, hoome);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. If available fast access memory is not low then this returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear some chunks. Chunks in {@link #notToClear} are not cleared
     * unless desperate. No chunk in {@code g} is cleared.
     *
     * @param g The grid from which chunks are not cleared.
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws java.io.IOException If encountered.
     */
    protected boolean checkAndMaybeFreeMemory(Grids_Grid g) throws IOException,
            Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            notToClear.put(g, g.getChunkIDs());
            do {
                if (!swapChunkExcept(notToClear)) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                if (!swapChunkExcept(g)) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            return getTotalFreeMemory() < Memory_Threshold;
        } else {
            return true;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. The chunk with chunk ID {@code i} in {@code g} is not cleared. If
     * available fast access memory is not low then this simply returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear chunks with IDs not in {@link #notToClear}. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param g The grid from which the chunk with ID {@code i} is not cleared.
     * @param i The chunk from {@code g} that is not cleared.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory(Grids_Grid g, Grids_2D_ID_int i,
            boolean hoome) throws IOException, Exception {
        try {
            if (!checkAndMaybeFreeMemory(g, i)) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory(" + g.getClass().getName()
                        + ",Grids_2D_ID_int,boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return true;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!checkAndMaybeFreeMemory(g, i)) {
                    throw e;
                }
                initMemoryReserve(g, i, hoome);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by clearing chunks.
     * The chunk with chunk ID {@code i} in {@code g} is not cleared. If
     * available fast access memory is not low then this simply returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear chunks with IDs not in {@link #notToClear}. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param g The grid from which the chunk with ID {@code i} is not cleared.
     * @param i The chunk from {@code g} that is not cleared.
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean checkAndMaybeFreeMemory(Grids_Grid g,
            Grids_2D_ID_int i) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            addToNotToClear(g, i);
            do {
                if (!swapChunkExcept(notToClear)) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                if (swapChunkExcept_Account(g, i) < 1) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            return getTotalFreeMemory() < Memory_Threshold;
        } else {
            return true;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. If available fast access memory is not low then this simply returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear chunks with IDs not in {@link #notToClear}. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param i The chunk ID of chunks not to be cleared.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory(Grids_2D_ID_int i, boolean hoome)
            throws IOException, Exception {
        try {
            if (!checkAndMaybeFreeMemory(i)) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory(Grids_2D_ID_int,boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return true;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!checkAndMaybeFreeMemory(i)) {
                    throw e;
                }
                initMemoryReserve(i, hoome);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. No chunks with chunk ID {@code i} are cleared. If available fast
     * access memory is not low then this simply returns {@code true}. If
     * available fast access memory is low, then an attempt is made to clear
     * chunks with IDs not in {@link #notToClear}. If this is unsuccessful in
     * clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param i The chunk ID of chunks not to be cleared.
     * @return {@code true} if {@code getTotalFreeMemory() < Memory_Threshold}
     * or if sufficient memory is freed so that this is the case.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean checkAndMaybeFreeMemory(Grids_2D_ID_int i)
            throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Iterator<Grids_Grid> ite = grids.iterator();
            while (ite.hasNext()) {
                addToNotToClear(ite.next(), i);
                if (swapChunkExcept(notToClear)) {
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        return true;
                    }
                }
            }
            ite = grids.iterator();
            while (ite.hasNext()) {
                if (swapChunkExcept(i)) {
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        return true;
                    }
                }
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. No data in {@code m} is cleared. If available fast access memory is
     * not low then this simply returns {@code true}. If available fast access
     * memory is low, then an attempt is made to clear chunks with IDs not in
     * {@link #notToClear}. If this is unsuccessful in clearing sufficient
     * memory such that {@code getTotalFreeMemory() < Memory_Threshold}, then
     * chunks with IDs in {@link #notToClear} are cleared. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param m Indicates which chunks not to clear unless deperate.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return {@code true} if there is sufficient memory to continue and {code
     * false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m,
            boolean hoome) throws IOException, Exception {
        try {
            if (!checkAndMaybeFreeMemory(m)) {
                String message = "Warning! No data to clear in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory(" + m.getClass().getName()
                        + ",boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return true;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!checkAndMaybeFreeMemory(m)) {
                    throw e;
                }
                initMemoryReserve(m, hoome);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. No data in {@code m} is cleared. If available fast access memory is
     * not low then this simply returns {@code true}. If available fast access
     * memory is low, then an attempt is made to clear chunks with IDs not in
     * {@link #notToClear}. If this is unsuccessful in clearing sufficient
     * memory such that {@code getTotalFreeMemory() < Memory_Threshold}, then
     * chunks with IDs in {@link #notToClear} are cleared. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param m Indicates which chunks not to clear unless deperate.
     * @return {@code true} if there is sufficient memory to continue and {code
     * false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean checkAndMaybeFreeMemory(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) throws IOException,
            Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            addToNotToClear(m);
            do {
                if (!swapChunkExcept(notToClear)) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                if (swapChunkExcept_Account(m) < 1) {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            return getTotalFreeMemory() < Memory_Threshold;
        } else {
            return true;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. No chunks from {@code g} with chunk IDs in {@code s} are cleared.
     * If available fast access memory is not low then this simply returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear chunks with IDs not in {@link #notToClear}. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param g The grid from which no chunks with chunk IDs in {@code s} are
     * cleared.
     * @param s The chunk IDs of chunks in {@code g} that are not cleared.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public boolean checkAndMaybeFreeMemory(Grids_Grid g, Set<Grids_2D_ID_int> s,
            boolean hoome) throws IOException, Exception {
        try {
            while (getTotalFreeMemory() < Memory_Threshold) {
                if (swapChunkExcept_Account(g, s) < 1) {
                    env.log("Warning! No data to clear in "
                            + this.getClass().getName()
                            + ".checkAndMaybeFreeMemory(" + g.getClass().getName()
                            + "," + "Set<Grids_2D_ID_int>,boolean)");
                    // Set to exit method with OutOfMemoryError
                    hoome = false;
                    throw new OutOfMemoryError();
                }
            }
            return true;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                boolean createdRoom = false;
                while (!createdRoom) {
                    if (swapChunkExcept_Account(g, s) < 1L) {
                        System.out.println(
                                "Warning! Nothing to cache in "
                                + this.getClass().getName()
                                + ".checkAndMaybeFreeMemory(" + g.getClass().getName()
                                + ",Set<ChunkID>,boolean) after encountering "
                                + "an OutOfMemoryError");
                        throw e;
                    }
                    initMemoryReserve(g, s, hoome);
                    checkAndMaybeFreeMemory(g, s, hoome);
                    //createdRoom = true;
                    return true;
                }
            } else {
                throw e;
            }
        }
        return false;
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. No chunks from {@code g} with chunk IDs in {@code s} are cleared.
     * If available fast access memory is not low then this simply returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear chunks with IDs not in {@link #notToClear}. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param g The grid from which no chunks with chunk IDs in {@code s} are
     * cleared.
     * @param s The chunk IDs of chunks in {@code g} that are not cleared.
     * @return {@code true} if there is sufficient memory to continue and
     * {@code false} otherwise.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean checkAndMaybeFreeMemory(Grids_Grid g,
            Set<Grids_2D_ID_int> s) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            addToNotToClear(g, s);
            do {
                if (swapChunkExcept(notToClear)) {
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        return true;
                    }
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                if (swapChunkExcept_Account(g, s) < 1) {
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        return true;
                    }
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            return getTotalFreeMemory() < Memory_Threshold;
        } else {
            return true;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. If available fast access memory is not low then this simply returns
     * {@code true}. If available fast access memory is low, then an attempt is
     * made to clear chunks with IDs not in {@link #notToClear}. If this is
     * unsuccessful in clearing sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then chunks with IDs in
     * {@link #notToClear} are cleared. If this is unsuccessful in clearing
     * sufficient memory such that
     * {@code getTotalFreeMemory() < Memory_Threshold}, then {@code false} is
     * returned.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return The number of chunks cleared.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_Account checkAndMaybeFreeMemory_Account(
            boolean hoome) throws IOException, Exception {
        try {
            Grids_Account test = checkAndMaybeFreeMemory_Account();
            if (!test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_Account(boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                Grids_Account r = checkAndMaybeFreeMemory_Account(hoome);
                r.add(initMemoryReserve_Account(hoome));
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file.
     *
     * @return Grids_Account of data cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account checkAndMaybeFreeMemory_Account() throws IOException,
            Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_Account r = new Grids_Account();
            do {
                if (swapChunkExcept(notToClear)) {
                    r.detail++;
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            if (getTotalFreeMemory() < Memory_Threshold) {
                r.success = true;
            } else {
                do {
                    if (swapChunk()) {
                        r.detail++;
                    } else {
                        break;
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
                r.success = getTotalFreeMemory() < Memory_Threshold;
            }
            return r;
        } else {
            return null;
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. This method may throw
     * an OutOfMemoryError if there is not enough data to cache in Grids. No
     * data is cached from g.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return Number of chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_Grid g,
            boolean hoome) throws IOException, Exception {
        try {
            Grids_Account test = checkAndMaybeFreeMemory_Account(g);
            if (test == null) {
                return 0;
            }
            if (!test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_Account(" + g.getClass().getName()
                        + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = checkAndMaybeFreeMemory_Account(g, hoome);
                r += initMemoryReserve_Account(g, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. This method may throw
     * an OutOfMemoryError if there is not enough data to cache in Grids. No
     * data is cached from g.
     *
     * @param g
     * @return Grids_Account of data cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account checkAndMaybeFreeMemory_Account(Grids_Grid g)
            throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_Account r = new Grids_Account();
            addToNotToClear(g);
            do {
                if (swapChunkExcept(notToClear)) {
                    r.detail++;
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            if (getTotalFreeMemory() < Memory_Threshold) {
                r.success = true;
            } else {
                do {
                    if (swapChunkExcept(g)) {
                        r.detail++;
                    } else {
                        break;
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
                r.success = getTotalFreeMemory() < Memory_Threshold;
            }
            return r;
        }
        return null;
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. This method may throw
     * an OutOfMemoryError if there is not enough data to cache in Grids. The
     * Chunk with chunkID from g is not cached.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @param chunkID
     * @return Number of chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_Grid g,
            Grids_2D_ID_int chunkID, boolean hoome) throws IOException, Exception {
        try {
            Grids_Account r = checkAndMaybeFreeMemory_Account(g, chunkID);
            if (r == null) {
                return 0;
            }
            if (!r.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_Account("
                        + g.getClass().getName() + ","
                        + chunkID.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return r.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = checkAndMaybeFreeMemory_Account(g, chunkID, hoome);
                r += initMemoryReserve_Account(g, chunkID, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. This method may throw
     * an OutOfMemoryError if there is not enough data to cache in Grids. The
     * Chunk with chunkID from g is not cached.
     *
     * @param g
     * @param i
     * @return Grids_Account of data cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_Grid g,
            Grids_2D_ID_int i) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_Account r = new Grids_Account();
            addToNotToClear(g, i);
            do {
                if (swapChunkExcept(notToClear)) {
                    r.detail++;
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            if (getTotalFreeMemory() < Memory_Threshold) {
                r.success = true;
            } else {
                do {
                    long caches = swapChunkExcept_Account(g, i);
                    if (caches < 1L) {
                        break;
                    } else {
                        r.detail += caches;
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
                r.success = getTotalFreeMemory() < Memory_Threshold;
            }
            return r;
        }
        return null;
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. This method may throw
     * an OutOfMemoryError if there is not enough data to cache in Grids. No
     * Chunk with chunkID is not cached.
     *
     * @param i
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return Number of chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_2D_ID_int i,
            boolean hoome) throws IOException, Exception {
        try {
            Grids_Account r = checkAndMaybeFreeMemory_Account(i);
            if (r == null) {
                return 0;
            }
            if (!r.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_Account("
                        + i.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return r.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = checkAndMaybeFreeMemory_Account(i, hoome);
                r += initMemoryReserve_Account(i, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. No Chunk with chunkID
     * is not cached.
     *
     * @param i
     * @return Grids_Account of data cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_2D_ID_int i)
            throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_Account r = new Grids_Account();
            Iterator<Grids_Grid> ite = grids.iterator();
            while (ite.hasNext()) {
                Grids_Grid g = ite.next();
                addToNotToClear(g, i);
                long cache = swapChunkExcept_Account(notToClear);
                r.detail += cache;
                if (cache > 0L) {
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            }
            ite = grids.iterator();
            while (ite.hasNext()) {
                Grids_Grid g = ite.next();
                long cache = swapChunkExcept_Account(g, i);
                r.detail += cache;
                if (cache > 0L) {
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. No data is cached as
     * identified by m.
     *
     * @param m
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return Number of chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_Account checkAndMaybeFreeMemory_Account(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m,
            boolean hoome) throws IOException, Exception {
        try {
            Grids_Account test = checkAndMaybeFreeMemory_Account(m);
            if (test == null) {
                return 0;
            }
            if (!test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_Account("
                        + m.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = checkAndMaybeFreeMemory_Account(m, hoome);
                r += initMemoryReserve_Account(m, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. No data is cached as
     * identified by m.
     *
     * @param m
     * @return Grids_Account of data cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account checkAndMaybeFreeMemory_Account(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_Account r = new Grids_Account();
            addToNotToClear(m);
            do {
                if (swapChunkExcept(notToClear)) {
                    r.detail++;
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            if (getTotalFreeMemory() < Memory_Threshold) {
                r.success = true;
            } else {
                do {
                    long caches = swapChunkExcept_Account(m);
                    if (caches < 1L) {
                        break;
                    } else {
                        r.detail += caches;
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
                r.success = getTotalFreeMemory() < Memory_Threshold;
            }
            return r;
        }
        return null;
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. No data is cached as
     * identified by m. No data is cached from chunks in g.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @param chunks
     * @return Number of chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_Grid g,
            Set<Grids_2D_ID_int> chunks, boolean hoome) throws IOException, Exception {
        try {
            Grids_Account a = checkAndMaybeFreeMemory_Account(g, chunks);
            if (!a.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_Account("
                        + g.getClass().getName() + ","
                        + chunks.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return a;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                return freeSomeMemoryAndResetReserve_Account(g, chunks, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to check and maybe free fast access memory by writing chunks to
     * file. An attempt at Grids internal memory handling is performed if an
     * OutOfMemoryError is encountered and hoome is true. No data is cached as
     * identified by m. No data is cached from chunks in g.
     *
     * @param g
     * @param chunkIDs
     * @return Grids_Account of data cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account checkAndMaybeFreeMemory_Account(Grids_Grid g,
            Set<Grids_2D_ID_int> chunkIDs) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_Account r = new Grids_Account();
            addToNotToClear(g, chunkIDs);
            do {
                if (swapChunkExcept(notToClear)) {
                    r.detail++;
                } else {
                    break;
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            if (getTotalFreeMemory() < Memory_Threshold) {
                r.success = true;
            } else {
                do {
                    long caches = swapChunkExcept_Account(g, chunkIDs);
                    if (caches < 1L) {
                        break;
                    } else {
                        r.detail += caches;
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
                r.success = getTotalFreeMemory() < Memory_Threshold;
            }
            return r;
        }
        return null;
    }

    /**
     * A method to ensure there is enough memory to continue. An attempt at
     * Grids internal memory handling is performed if an OutOfMemoryError is
     * encountered and hoome is true. This method may throw an OutOfMemoryError
     * if there is no grid chunk to cache in Grids.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return A map of the grid chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(boolean hoome)
            throws IOException, Exception {
        try {
            Grids_AccountDetail a = checkAndMaybeFreeMemory_AccountDetail();
            if (!a.success) {
                String message;
                message = "Warning! Not enough data to cache in "
                        + getClass().getName()
                        + ".checkAndMaybeFreeMemory_AccountDetail("
                        + "boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return a;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                return freeSomeMemoryAndResetReserve_AccountDetails(hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue.
     *
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail()
            throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_AccountDetail r = new Grids_AccountDetail();
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
            do {
                pr = swapChunkExcept_AccountDetail(notToClear);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                pr = swapChunk_AccountDetail();
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            r.success = false;
            return r;
        }
        return null;
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. An attempt at Grids internal memory handling is
     * performed if an OutOfMemoryError is encountered and hoome is true. This
     * method may throw an OutOfMemoryError if there is not enough data to cache
     * in Grids. No data is cached from g.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap identifying chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            Grids_AccountDetail test = checkAndMaybeFreeMemory_AccountDetail(g);
            if (test == null) {
                return null;
            }
            if (!test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_AccountDetail("
                        + g.getClass().getName() + ",boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                return freeSomeMemoryAndResetReserve_AccountDetails(g, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. No data is cached from g.
     *
     * @param g
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(
            Grids_Grid g) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_AccountDetail r = new Grids_AccountDetail();
            addToNotToClear(g);
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
            do {
                pr = swapChunkExcept_AccountDetail(notToClear);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                pr = swapChunkExcept_AccountDetail(g);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            r.success = false;
            return r;
        }
        return null;
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @param i
     * @return HashMap identifying chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(Grids_Grid g,
                    Grids_2D_ID_int i, boolean hoome) throws IOException,
            Exception {
        try {
            Grids_AccountDetail test = checkAndMaybeFreeMemory_AccountDetail(g, i);
            if (test == null) {
                return null;
            }
            if (!test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_AccountDetail("
                        + g.getClass().getName() + ","
                        + i.getClass().getName() + ",boolean)";
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = checkAndMaybeFreeMemory_AccountDetail(g, i, hoome);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = initMemoryReserve_AccountDetail(g, i, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached.
     *
     * @param g
     * @param chunkID
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(
            Grids_Grid g, Grids_2D_ID_int chunkID) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_AccountDetail r = new Grids_AccountDetail();
            addToNotToClear(g, chunkID);
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
            do {
                pr = swapChunkExcept_AccountDetail(notToClear);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                pr = swapChunkExcept_AccountDetail(g, chunkID);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            r.success = false;
            return r;
        }
        return null;
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached. No data
     * is cached with chunkID.
     *
     * @param chunkID
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap identifying chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(Grids_2D_ID_int chunkID,
                    boolean hoome) throws IOException, Exception {
        try {
            Grids_AccountDetail r = checkAndMaybeFreeMemory_AccountDetail(chunkID);
            if (r == null) {
                return null;
            }
            if (!r.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_AccountDetail("
                        + chunkID.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return r.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = checkAndMaybeFreeMemory_AccountDetail(chunkID, hoome);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = initMemoryReserve_AccountDetail(chunkID, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached. No data
     * is cached with chunkID.
     *
     * @param chunkID
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(
            Grids_2D_ID_int i) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_AccountDetail r = new Grids_AccountDetail();
            Iterator<Grids_Grid> ite = grids.iterator();
            while (ite.hasNext()) {
                Grids_Grid g = ite.next();
                addToNotToClear(g, i);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
                do {
                    pr = swapChunkExcept_AccountDetail(notToClear);
                    if (pr.isEmpty()) {
                        break;
                    } else {
                        combine(r.detail, pr);
                        if (getTotalFreeMemory() < Memory_Threshold) {
                            r.success = true;
                            return r;
                        }
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
            }
            ite = grids.iterator();
            while (ite.hasNext()) {
                Grids_Grid g = ite.next();
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
                do {
                    pr = swapChunkExcept_AccountDetail(g, i);
                    if (pr.isEmpty()) {
                        break;
                    } else {
                        combine(r.detail, pr);
                        if (getTotalFreeMemory() < Memory_Threshold) {
                            r.success = true;
                            return r;
                        }
                    }
                } while (getTotalFreeMemory() < Memory_Threshold);
            }
            r.success = false;
            return r;
        }
        return null;
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached. No data
     * is cached as identified by m.
     *
     * @param m Identifies data not to be cached.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap identifying chunks cached or null if nothing is cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m, boolean hoome)
            throws IOException, Exception {
        try {
            Grids_AccountDetail test = checkAndMaybeFreeMemory_AccountDetail(m);
            if (test == null) {
                return null;
            }
            if (test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_AccountDetail("
                        + m.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = checkAndMaybeFreeMemory_AccountDetail(m, hoome);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = initMemoryReserve_AccountDetail(m, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached. No data
     * is cached as identified by m.
     *
     * @param m Identifies data not to be cached.
     * @return HashMap identifying chunks cached or null if nothing is cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) throws IOException,
            Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_AccountDetail r = new Grids_AccountDetail();
            addToNotToClear(m);
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
            do {
                pr = swapChunkExcept_AccountDetail(notToClear);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                pr = swapChunkExcept_AccountDetail(m);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            r.success = false;
            return r;
        }
        return null;
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached. No
     * chunks with ChunkID in chunkIDs are cached from g.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @param s
     * @return HashMap identifying chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(Grids_Grid g,
                    Set<Grids_2D_ID_int> s, boolean hoome) throws IOException,
            Exception {
        try {
            Grids_AccountDetail test = checkAndMaybeFreeMemory_AccountDetail(g, s);
            if (test == null) {
                return null;
            }
            if (!test.success) {
                String message = "Warning! Not enough data to cache in "
                        + this.getClass().getName()
                        + ".checkAndMaybeFreeMemory_AccountDetail("
                        + g.getClass().getName() + ","
                        + s.getClass().getName() + ",boolean)";
                System.out.println(message);
                // Set to exit method with OutOfMemoryError
                hoome = false;
                throw new OutOfMemoryError(message);
            }
            return test.detail;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                return freeSomeMemoryAndResetReserve_AccountDetails(g, s,
                        hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * A method to ensure there is enough memory to continue. This method will
     * quickly return null if there is enough memory to continue. If there is
     * not enough memory to continue it will attempt to make room and will pass
     * back a detailed account of this and an indication if there is enough
     * memory to continue. The Chunk with chunkID from g is not cached. No
     * chunks with ChunkID in chunkIDs are cached from g.
     *
     * @param g
     * @param s
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail checkAndMaybeFreeMemory_AccountDetail(
            Grids_Grid g, Set<Grids_2D_ID_int> s) throws IOException, Exception {
        if (getTotalFreeMemory() < Memory_Threshold) {
            Grids_AccountDetail r = new Grids_AccountDetail();
            addToNotToClear(g, s);
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr;
            do {
                pr = swapChunkExcept_AccountDetail(notToClear);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            do {
                pr = swapChunkExcept_AccountDetail(g, s);
                if (pr.isEmpty()) {
                    break;
                } else {
                    combine(r.detail, pr);
                    if (getTotalFreeMemory() < Memory_Threshold) {
                        r.success = true;
                        return r;
                    }
                }
            } while (getTotalFreeMemory() < Memory_Threshold);
            r.success = false;
            return r;
        }
        return null;
    }

    /**
     * Attempts to cache all chunks in grids.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory. OutOfMemoryErrors are caught and thrown.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public HashMap<Grids_Grid, Set<Grids_2D_ID_int>> swapChunks_AccountDetail(
            boolean hoome) throws IOException, Exception, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunks_AccountDetail(notToClear);
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account = checkAndMaybeFreeMemory_AccountDetail();
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                            = checkAndMaybeFreeMemory_AccountDetail(hoome);
                    combine(r, pr);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                freeSomeMemoryAndResetReserve_AccountDetails(e, hoome);
                return swapChunks_AccountDetail(hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to cache all Grids_Chunk in this.grids.
     *
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunks_AccountDetail() throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>();
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            combine(r, ite.next().swapChunks_AccountDetail());
        }
        return r;
    }

    /**
     * Attempts to cache all Grids_Chunk in this.grids.
     *
     * @param m
     * @return
     */
    protected HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunks_AccountDetail(
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m)
            throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>();
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                    = g.swapChunksExcept_AccountDetail(m.get(g));
            combine(r, pr);
        }
        return r;
    }

    /**
     * Attempts to cache all chunks in env.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory. OutOfMemoryErrors are caught and thrown.
     * @return A count of the number of chunks cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public long swapChunks_Account(boolean hoome) throws IOException,
            Exception {
        try {
            long r;
            try {
                r = swapChunks_Account();
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account();
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(hoome);
                }
            } catch (OutOfMemoryError e) {
                /**
                 * Set hoome = false to exit method by throwing OutOfMemoryError
                 */
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = freeSomeMemoryAndResetReserve_Account(e, hoome);
                r += swapChunks_Account(hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to cache all chunks in env.
     *
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected long swapChunks_Account() throws IOException, Exception {
        long r = 0L;
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            r += ite.next().swapChunks_Account();
        }
        dataToClear = false;
        return r;
    }

    /**
     * Attempts to cache all Grids_Chunk in this.grids.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public void swapChunks(boolean hoome) throws IOException, Exception {
        try {
            boolean success = swapChunks();
            try {
                if (!success) {
                    checkAndMaybeFreeMemory();
                } else {
                    checkAndMaybeFreeMemory(hoome);
                }
            } catch (OutOfMemoryError e) {
                /**
                 * Set hoome = false to exit method by throwing OutOfMemoryError
                 */
                hoome = false;
                throw e;
            }
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!swapChunk()) {
                    throw e;
                }
                initMemoryReserve(env);
                swapChunks();
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to cache all Grids_Chunk in grids.
     *
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean swapChunks() throws IOException, Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            ite.next().swapChunks();
        }
        dataToClear = false;
        return true;
    }

    /**
     * Attempts to cache any Grids_AbstractGridChunk in this.Grids. This is the
     * lowest level of OutOfMemoryError handling in this class.
     *
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_AccountDetail swapChunk_AccountDetail(boolean hoome) throws IOException,
            Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunk_AccountDetail();
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account
                            = checkAndMaybeFreeMemory_AccountDetail();
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    combine(r, checkAndMaybeFreeMemory_AccountDetail(hoome));
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                return freeSomeMemoryAndResetReserve_AccountDetails(e, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to cache any chunk in grids trying first not to cache any in
     * notToClear.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public boolean swapChunk(boolean hoome) throws IOException, Exception {
        try {
            boolean success = swapChunk();
            try {
                if (!success) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account();
                    if (account != null) {
                        if (!account.success) {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    checkAndMaybeFreeMemory(hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return true;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!swapChunk()) {
                    throw e;
                }
                initMemoryReserve(env);
                // No need for recursive call: swap(hoome);
                return true;
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to cache any chunk in grids trying first not to cache any in
     * notToClear.
     *
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunk_Account() throws IOException, Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        Grids_Grid g = ite.next();
        Grids_Account r = swapChunkExcept_Account(g, notToClear);
        if (r.success) {
            return r;
        }
        while (ite.hasNext()) {
            g = ite.next();
            Grids_Account pr = swapChunkExcept_Account(g, notToClear);
            if (pr.success) {
                r.add(pr);
                return r;
            }
            if (g.swapChunk() != null) {
                return true;
            }
        }
        dataToClear = false;
        return false;
    }

    /**
     * Cache to File any GridChunk in grids except one in g.
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public void swapChunkExcept(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            boolean success = swapChunkExcept(g);
            try {
                if (!success) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(g);
                    if (account != null) {
                        if (!account.success) {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    checkAndMaybeFreeMemory(g, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (swapChunkExcept_Account(g) < 1L) {
                    throw e;
                }
                initMemoryReserve(g, hoome);
            } else {
                throw e;
            }
        }
    }

    /**
     * Cache to File any GridChunk in grids except one in g.
     *
     * @param g
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean swapChunkExcept(Grids_Grid g) throws IOException,
            Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid bg = ite.next();
            if (bg != g) {
                if (bg.swapChunk() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Attempts to cache any Grids_Chunk in this.grids.
     *
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail swapChunk_AccountDetail() throws IOException,
            Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            if (notToClear.containsKey(g)) {
                Grids_AccountDetail r = g.swapChunkExcept_AccountDetail(
                        notToClear.get(g));
                if (!r.detail.isEmpty()) {
                    return r;
                }
            }
        }
        dataToClear = false;
        return null;
    }

    /**
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in with
     * Grids_Grid.ID = _ChunkID.
     * @param chunkID The Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_AccountDetail swapChunkExcept_AccountDetail(Grids_2D_ID_int chunkID,
                    boolean hoome) throws IOException, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r;
            r = swapChunkExcept_AccountDetail(chunkID);
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account
                            = checkAndMaybeFreeMemory_AccountDetail(chunkID);
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                            = checkAndMaybeFreeMemory_AccountDetail(chunkID,
                                    hoome);
                    combine(r, pr);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(chunkID);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = initMemoryReserve_AccountDetail(chunkID, hoome);
                combine(r, pr);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in with
     * Grids_Grid.ID = _ChunkID.
     * @param chunkID The Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail swapChunkExcept_AccountDetail(Grids_2D_ID_int chunkID)
            throws IOException, Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = g.swapChunkExcept_AccountDetail(chunkID);
            if (!r.isEmpty()) {
                Set<Grids_2D_ID_int> s = new HashSet<>(1);
                s.add(chunkID);
                r.put(g, s);
                return r;
            }
        }
        return null;
    }

    /**
     *
     * @param i
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunkExcept_Account(Grids_2D_ID_int i, boolean hoome)
            throws IOException, Exception {
        try {
            long r = swapChunkExcept_Account(i);
            try {
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(i);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(i, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            r += checkAndMaybeFreeMemory_Account(hoome);
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(i);
                if (r < 1L) {
                    throw e;
                }
                r += initMemoryReserve_Account(i, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * @param i The id of the GridChunk not to be cached.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunkExcept_Account(Grids_2D_ID_int i)
            throws IOException, Exception {
        long r = 0L;
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            addToNotToClear(g, i);
            if (!swapChunkExcept(notToClear)) {
                r += swapChunkExcept_Account(i);
            } else {
                r += 1L;
            }
            if (r > 0L) {
                return r;
            }
        }
        return r;
    }

    /**
     * @param i The ID of the chunk not to be cached.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected boolean swapChunkExcept(Grids_2D_ID_int i) throws IOException,
            Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            if (swapChunkExcept_Account(g, i) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in
     * _Grid2DSquareCell_ChunkIDSet.
     * @param m HashMap with Grids_Grid as keys and a respective Set of
     * Grids_Grid.ChunkIDs as values. Collectively these identifying those
     * chunks not to be cached from the Grids_Grid.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_AccountDetail swapChunkExcept_AccountDetail(
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m,
                    boolean hoome) throws IOException, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunkExcept_AccountDetail(m);
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account
                            = checkAndMaybeFreeMemory_AccountDetail(m);
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    combine(r, checkAndMaybeFreeMemory_AccountDetail(m, hoome));
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(m);
                if (r.isEmpty()) {
                    throw e;
                }
                initMemoryReserve(m, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * @param m
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in
     * _Grid2DSquareCell_ChunkIDSet.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail  swapChunkExcept_AccountDetail(
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m)
            throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>(1);
        Iterator<Grids_Grid> ite = grids.iterator();
        Set<Grids_2D_ID_int> s = new HashSet<>(1);
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            if (m.containsKey(g)) {
                Grids_2D_ID_int i = g.swapChunkExcept_AccountChunk(m.get(g));
                if (i != null) {
                    s.add(i);
                    r.put(g, s);
                    return r;
                }
            }
            Grids_2D_ID_int i = g.swapChunk_AccountChunk();
            if (i != null) {
                s.add(i);
                r.put(g, s);
                return r;
            }
        }
        return r; // If here then nothing could be cached!
    }

    /**
     *
     * @param g
     * @param chunkIDs
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail swapChunkExcept_AccountDetail(Grids_Grid g,
                    Set<Grids_2D_ID_int> s) throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>(1);
        Iterator<Grids_Grid> ite = grids.iterator();
        Set<Grids_2D_ID_int> rp = new HashSet<>(1);
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (g == gb) {
                Grids_2D_ID_int i = gb.swapChunkExcept_AccountChunk(s);
                if (i != null) {
                    rp.add(i);
                    r.put(g, rp);
                    return r;
                }
            } else {
                Grids_2D_ID_int i = g.swapChunk_AccountChunk();
                if (i != null) {
                    rp.add(i);
                    r.put(g, rp);
                    return r;
                }
            }
        }
        return r; // If here then nothing could be cached!
    }

    /**
     *
     * @param g
     * @param i
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail swapChunkExcept_AccountDetail(Grids_Grid g, Grids_2D_ID_int i)
            throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>(1);
        Iterator<Grids_Grid> ite = grids.iterator();
        Set<Grids_2D_ID_int> rp = new HashSet<>(1);
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (g == gb) {
                Grids_2D_ID_int i2 = gb.swapChunkExcept_AccountChunk(i);
                if (i2 != null) {
                    rp.add(i2);
                    r.put(g, rp);
                    return r;
                }
            } else {
                Grids_2D_ID_int i2 = g.swapChunk_AccountChunk();
                if (i2 != null) {
                    rp.add(i2);
                    r.put(g, rp);
                    return r;
                }
            }
        }
        return r; // If here then nothing could be cached!
    }

    /**
     *
     * @param g
     * @param i
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_AccountDetail swapChunkExcept_AccountDetail(Grids_Grid g, Grids_2D_ID_int i,
                    boolean hoome) throws IOException, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunkExcept_AccountDetail(g, i);
            return r;
        } catch (java.lang.OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                        = swapChunkExcept_AccountDetail(g, i);
                if (rp.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = initMemoryReserve_AccountDetail(g, i, hoome);
                combine(r, rp);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     *
     * @param g
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail swapChunkExcept_AccountDetail(Grids_Grid g) throws IOException,
            Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>(1);
        Iterator<Grids_Grid> ite = grids.iterator();
        Set<Grids_2D_ID_int> rp = new HashSet<>(1);
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (g != gb) {
                Grids_2D_ID_int i = gb.swapChunk_AccountChunk();
                if (i != null) {
                    rp.add(i);
                    r.put(g, rp);
                    return r;
                }
            }
        }
        return r; // If here then nothing could be cached!
    }

    /**
     *
     * @param m
     * @return
     */
    protected Grids_Account swapChunkExcept_Account(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) throws IOException,
            Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            if (m.containsKey(g)) {
                Grids_2D_ID_int i = g.swapChunkExcept_AccountChunk(m.get(g));
                if (i != null) {
                    return 1L;
                }
            }
            Grids_2D_ID_int i = g.swapChunk_AccountChunk();
            if (i != null) {
                return 1L;
            }
        }
        return 0L; // If here then nothing could be cached!
    }

//    /**
//     *
//     * @param m
//     * @return
//     * @throws IOException If encountered.
//     * @throws Exception If encountered.
//     */
//    protected boolean swapChunkExcept_Account(
//            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) throws IOException,
//            Exception {
//        Iterator<Grids_Grid> ite = grids.iterator();
//        while (ite.hasNext()) {
//            Grids_Grid g = ite.next();
//            if (m.containsKey(g)) {
//                if (g.swapChunkExcept_AccountChunk(m.get(g)) != null) {
//                    return true;
//                }
//            }
//            if (g.swapChunk_AccountChunk() != null) {
//                return true;
//            }
//        }
//        return false;
//    }
    /**
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @param s
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in g with ChunkIDs
     * in chunkIDs.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunkExcept_Account(Grids_Grid g, Set<Grids_2D_ID_int> s,
            boolean hoome) throws IOException, Exception {
        try {
            long r = swapChunkExcept_Account(g, s);
            try {
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(g, s);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(g, s, hoome);
                }
            } catch (OutOfMemoryError e) {
                /**
                 * Set hoome = false to exit method by throwing OutOfMemoryError
                 */
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g, s);
                if (r < 1L) {
                    throw e;
                }
                r += initMemoryReserve_Account(g, s, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * @param s
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in g with ChunkIDs
     * in chunkIDs.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunkExcept_Account(Grids_Grid g,
            Set<Grids_2D_ID_int> s) throws IOException, Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g2 = ite.next();
            if (g2 != g) {
                TreeMap<Grids_2D_ID_int, Grids_Chunk> m
                        = g2.getData();
                Set<Grids_2D_ID_int> s2 = m.keySet();
                Iterator<Grids_2D_ID_int> iteb = s2.iterator();
                while (iteb.hasNext()) {
                    Grids_2D_ID_int i = iteb.next();
                    if (!s.contains(i)) {
                        //Check it can be cached
                        if (m.get(i) != null) {
                            g2.swapChunk(i);
                            return 1;
                        }
                    }
                }
            }
        }
        return 0L;
    }

    /**
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for that in _Grid2DSquareCell
     * with Grids_Grid._ChunkID _ChunkID.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @param i The Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunkExcept_Account(Grids_Grid g, Grids_2D_ID_int i,
            boolean hoome) throws IOException, Exception {
        try {
            int r = swapChunkExcept_Account(g, i);
            try {
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(g, i);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(g, i, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                int r = swapChunkExcept_Account(g, i);
                if (r < 1) {
                    throw e;
                }
                r += initMemoryReserve_Account(g, i, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for that in _Grid2DSquareCell
     * with Grids_Grid._ChunkID _ChunkID.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @param chunkID The Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunkExcept_Account(Grids_Grid g,
            Grids_2D_ID_int chunkID) throws IOException, Exception {
        int r = swapChunkExcept_Account(g);
        if (r < 1L) {
            r = g.swapChunkExcept_Account(chunkID);
        }
        return r;
    }

    /**
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in g.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunkExcept_Account(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            long r = swapChunkExcept_Account(g);
            try {
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(g);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(g, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g);
                if (r < 1L) {
                    throw e;
                }
                r += initMemoryReserve_Account(g, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. Attempts to
     * cache any Grids_Chunk in this.grids except for those in
     * _Grid2DSquareCell.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunkExcept_Account(Grids_Grid g) throws IOException,
            Exception {
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (gb != g) {
                Grids_2D_ID_int i = gb.swapChunk_AccountChunk();
                if (i != null) {
                    return 1;
                }
            }
        }
        return 0;
    }

    /**
     * Attempts to Cache all Grids_Grid.ChunkIDs in this.grids except those with
     * Grids_Grid.ID _ChunkID.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param chunkID The i.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_AccountDetail swapChunksExcept_AccountDetail(Grids_2D_ID_int i, boolean hoome)
            throws IOException, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunksExcept_AccountDetail(i);
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account
                            = checkAndMaybeFreeMemory_AccountDetail(i);
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                            = checkAndMaybeFreeMemory_AccountDetail(i, hoome);
                    combine(r, rp);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(i);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                        = initMemoryReserve_AccountDetail(i, hoome);
                combine(r, rp);
                rp = swapChunksExcept_AccountDetail(i, hoome);
                combine(r, rp);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to Cache all Grids_Grid.ChunkIDs in this.grids except those with
     * Grids_Grid.ID _ChunkID.
     *
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached. i chunkID The
     * Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail swapChunksExcept_AccountDetail(Grids_2D_ID_int i)
            throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>();
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            combine(r, g.swapChunksExcept_AccountDetail(i));
        }
        return r;
    }

    /**
     * Attempts to Cache all Grids_Grid.ChunkIDs in this.grids except those with
     * Grids_Grid.ID _ChunkID.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param g Grids_Grid that's chunks are not to be cached. cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunksExcept_AccountDetail(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunksExcept_AccountDetail(g);
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account
                            = checkAndMaybeFreeMemory_AccountDetail(g);
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                            = checkAndMaybeFreeMemory_AccountDetail(g, hoome);
                    combine(r, rp);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(g);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                        = initMemoryReserve_AccountDetail(g, hoome);
                combine(r, rp);
                rp = swapChunksExcept_AccountDetail(g, hoome);
                combine(r, rp);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     * Attempts to Cache all Grids_Grid.ChunkIDs in this.grids except those with
     * Grids_Grid.ID _ChunkID.
     *
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunksExcept_AccountDetail(Grids_Grid g) throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>();
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (gb != g) {
                combine(r, gb.swapChunks_AccountDetail());
            }
        }
        return r;
    }

    /**
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException
     * @throws Exception
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunksExcept_Account(Grids_Grid g, boolean hoome)
            throws IOException, Exception {
        try {
            long r = swapChunksExcept_Account(g);
            try {
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(g);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(g, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g);
                if (r < 1L) {
                    throw e;
                }
                r += initMemoryReserve_Account(g, hoome);
                r += swapChunksExcept_Account(g, hoome);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     *
     * @param g
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunksExcept_Account(Grids_Grid g) throws IOException,
            Exception {
        long r = 0L;
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (gb != g) {
                r += gb.env.swapChunks_Account();
            }
        }
        return r;
    }

    /**
     * Attempts to Cache all Grids_Grid.ChunkIDs in this.grids except those with
     * Grids_Grid.ID _ChunkID.
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @param i The Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunksExcept_AccountDetail(Grids_Grid g, Grids_2D_ID_int i,
                    boolean hoome) throws IOException, Exception {
        try {
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                    = swapChunksExcept_AccountDetail(g, i);
            try {
                if (r.isEmpty()) {
                    Grids_AccountDetail account
                            = checkAndMaybeFreeMemory_AccountDetail(g, i);
                    if (account != null) {
                        if (account.success) {
                            combine(r, account.detail);
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                            = checkAndMaybeFreeMemory_AccountDetail(g, i,
                                    hoome);
                    combine(r, rp);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                        = swapChunkExcept_AccountDetail(g, i);
                if (r.isEmpty()) {
                    throw e;
                }
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                        = initMemoryReserve_AccountDetail(g, i, hoome);
                combine(r, rp);
                rp = swapChunksExcept_AccountDetail(g, i, hoome);
                combine(r, rp);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     *
     * @param g
     * @param i
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunksExcept_Account(Grids_Grid g, Grids_2D_ID_int i,
            boolean hoome) throws IOException, Exception {
        try {
            long r = swapChunksExcept_Account(g, i);
            try {
                if (r < 1) {
                    Grids_Account account
                            = checkAndMaybeFreeMemory_Account(g, i);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(g, i, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                long r = swapChunkExcept_Account(g, i);
                if (r < 1L) {
                    throw e;
                }
                r += initMemoryReserve_Account(i, hoome);
                r += swapChunkExcept_Account(g, i);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     *
     * @param g
     * @param chunkID
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunksExcept_Account(Grids_Grid g,
            Grids_2D_ID_int chunkID) throws IOException, Exception {
        long r = 0L;
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (gb != g) {
                int cri0 = 0;
                int cci0 = 0;
                int cri1 = gb.getNChunkRows() - 1;
                int cci1 = gb.getNChunkCols() - 1;
                r += gb.swapChunks_Account(cri0, cci0, cri1, cci1);
            } else {
                r += gb.swapChunksExcept_Account(chunkID);
            }
        }
        return r;
    }

    /**
     * Attempts to Cache all Grids_Grid.ChunkIDs in this.grids except those with
     * Grids_Grid.ID _ChunkID.
     *
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @param chunkID The Grids_Grid.ID not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunksExcept_AccountDetail(Grids_Grid g,
                    Grids_2D_ID_int chunkID) throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r;
        r = new HashMap<>();
        Iterator<Grids_Grid> ite = grids.iterator();
        Grids_Grid bg;
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp;
        while (ite.hasNext()) {
            bg = ite.next();
            if (bg == g) {
                rp = bg.swapChunksExcept_AccountDetail(chunkID);
                combine(r, rp);
            } else {
                rp = bg.swapChunks_AccountDetail(false, HOOMEF);
                combine(r, rp);
            }
        }
        return r;
    }

    /**
     *
     * @param g
     * @param chunkIDs
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected HashMap<Grids_Grid, Set<Grids_2D_ID_int>>
            swapChunksExcept_AccountDetail(Grids_Grid g,
                    Set<Grids_2D_ID_int> chunkIDs) throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r = new HashMap<>();
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid gb = ite.next();
            if (gb != g) {
                HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                        = gb.swapChunks_AccountDetail();
                combine(r, pr);
            } else {
                Iterator<Grids_2D_ID_int> ite2 = g.getChunkIDs().iterator();
                while (ite2.hasNext()) {
                    Grids_2D_ID_int i = ite2.next();
                    if (!chunkIDs.contains(i)) {
                        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> pr
                                = swapChunksExcept_AccountDetail(i);
                        combine(r, pr);
                    }
                }
            }
        }
        return r;
    }

    /**
     * Attempts to Cache all chunks except those in g with Chunk IDs in
     * chunkIDs.
     *
     * @return HashMap with: key as the Grids_Grid from which the Grids_Chunk
     * was cached; and, value as the Grids_Chunk._ChunkID cached.
     * @param g Grids_Grid that's chunks are not to be cached.
     * @param chunkIDs The chunk IDs in g not to be cached.
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected long swapChunksExcept_Account(Grids_Grid g,
            Set<Grids_2D_ID_int> chunkIDs) throws IOException, Exception {
        long r = 0L;
        Iterator<Grids_Grid> ite = grids.iterator();
        Grids_Grid gb;
        while (ite.hasNext()) {
            gb = ite.next();
            if (gb != g) {
                int cri0 = 0;
                int cri1 = gb.getNChunkRows() - 1;
                int cci0 = 0;
                int cci1 = gb.getNChunkCols() - 1;
                r += gb.swapChunks_Account(cri0, cci0, cri1, cci1);
            } else {
                r += gb.swapChunksExcept_Account(chunkIDs);
            }
        }
        return r;
    }

    /**
     * @param m
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    public Grids_Account swapChunksExcept_Account(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m,
            boolean hoome) throws IOException, Exception {
        try {
            long r = swapChunksExcept_Account(m);
            try {
                if (r < 1) {
                    Grids_Account account = checkAndMaybeFreeMemory_Account(m);
                    if (account != null) {
                        if (account.success) {
                            r += account.detail;
                        } else {
                            throw new OutOfMemoryError();
                        }
                    }
                } else {
                    r += checkAndMaybeFreeMemory_Account(m, hoome);
                }
            } catch (OutOfMemoryError e) {
                // Set hoome = false to exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                if (!swapChunkExcept(m)) {
                    throw e;
                }
                long r = 1L;
                r += initMemoryReserve_Account(m, hoome);
                r += swapChunksExcept_Account(m);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     *
     * @param m
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account swapChunksExcept_Account(
            HashMap<Grids_Grid, Set<Grids_2D_ID_int>> m) throws IOException,
            Exception {
        long r = 0L;
        Iterator<Grids_Grid> ite = grids.iterator();
        while (ite.hasNext()) {
            Grids_Grid g = ite.next();
            r += g.swapChunksExcept_Account(m.get(g));
        }
        return r;
    }

    public void cacheData() throws IOException, Exception {
        swapChunks();
    }

    /**
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public boolean swapSomeData(boolean hoome) throws IOException, Exception {
        try {
            boolean r = swapChunk();
            try {
                if (!checkAndMaybeFreeMemory()) {
                    throw new OutOfMemoryError();
                }
            } catch (OutOfMemoryError e) {
                // Exit method by throwing OutOfMemoryError
                hoome = false;
                throw e;
            }
            return r;
        } catch (OutOfMemoryError e) {
            if (hoome) {
                clearMemoryReserve(env);
                boolean r = swapSomeData();
                initMemoryReserve(env);
                return r;
            } else {
                throw e;
            }
        }
    }

    /**
     *
     * @return @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    @Override
    public boolean swapSomeData() throws IOException, Exception {
        return swapChunk();
    }

    private boolean dataToClear = true;

    public boolean isDataToClear() {
        return dataToClear;
    }

    public void setDataToClear(boolean dataToClear) {
        this.dataToClear = dataToClear;
    }

    /**
     *
     * @param g
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail freeSomeMemoryAndResetReserve_AccountDetails(Grids_Grid g,
                    boolean hoome) throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                = checkAndMaybeFreeMemory_AccountDetail(g, hoome);
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                = initMemoryReserve_AccountDetail(g, hoome);
        combine(r, rp);
        return r;
    }

    /**
     *
     * @param g
     * @param chunks
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     *
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail freeSomeMemoryAndResetReserve_AccountDetails(
                    Grids_Grid g, Set<Grids_2D_ID_int> chunks, boolean hoome)
            throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                = checkAndMaybeFreeMemory_AccountDetail(g, chunks, hoome);
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                = initMemoryReserve_AccountDetail(g, chunks, hoome);
        combine(r, rp);
        return r;
    }

    /**
     *
     * @param e
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail freeSomeMemoryAndResetReserve_AccountDetails(OutOfMemoryError e,
                    boolean hoome) throws IOException, Exception {
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> r
                = swapChunk_AccountDetail();
        if (r.isEmpty()) {
            throw e;
        }
        HashMap<Grids_Grid, Set<Grids_2D_ID_int>> rp
                = initMemoryReserve_AccountDetail(hoome);
        combine(r, rp);
        return r;
    }

    /**
     *
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_AccountDetail freeSomeMemoryAndResetReserve_AccountDetails(
            boolean hoome) throws IOException, Exception {
        Grids_AccountDetail r = checkAndMaybeFreeMemory_AccountDetail(hoome);
        r.add(initMemoryReserve_AccountDetail(hoome));
        return r;
    }

    /**
     *
     * @param g
     * @param chunks
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account freeSomeMemoryAndResetReserve_Account(Grids_Grid g,
            Set<Grids_2D_ID_int> chunks, boolean hoome) throws IOException,
            Exception {
        Grids_Account r = checkAndMaybeFreeMemory_Account(g, chunks, hoome);
        r.add(initMemoryReserve_Account(g, chunks, hoome));
        return r;
    }

    /**
     *
     * @param e
     * @param hoome If {@code true} then if an {@link OutOfMemoryError} is
     * thrown, then an attempt is made to handle it by clearing data from the
     * memory.
     * @return
     * @throws IOException If encountered.
     * @throws Exception If encountered.
     */
    protected Grids_Account freeSomeMemoryAndResetReserve_Account(OutOfMemoryError e,
            boolean hoome) throws IOException, Exception {
        Grids_Account r = swapChunk_Account();
        if (!r.success) {
            throw e;
        }
        r.add(initMemoryReserve_Account(hoome));
        return r;
    }

    /**
     * @return {@link #notToClear}
     */
    public HashMap<Grids_Grid, Set<Grids_2D_ID_int>> getNotToClear() {
        return notToClear;
    }

}
