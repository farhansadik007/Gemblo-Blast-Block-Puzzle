package com.papercranelabs.gemblo.game

import kotlin.random.Random

const val N = 8
const val COLORS = 5

data class Cell(val r: Int, val c: Int)

/** A tray piece: cells relative to its top-left, one color (1..COLORS). */
class Piece(val cells: List<Cell>, val color: Int) {
    val w: Int = cells.maxOf { it.c } + 1
    val h: Int = cells.maxOf { it.r } + 1

    /** 90° clockwise. */
    fun rotated(): Piece = Piece(normalize(cells.map { Cell(it.c, h - 1 - it.r) }), color)
}

fun normalize(cells: List<Cell>): List<Cell> {
    val mr = cells.minOf { it.r }
    val mc = cells.minOf { it.c }
    return cells.map { Cell(it.r - mr, it.c - mc) }.sortedWith(compareBy<Cell>({ it.r }, { it.c }))
}

private fun shape(vararg rows: String): List<Cell> =
    rows.flatMapIndexed { r, row -> row.mapIndexedNotNull { c, ch -> if (ch == '#') Cell(r, c) else null } }

private fun rotations(cells: List<Cell>): List<List<Cell>> {
    val out = mutableListOf<List<Cell>>()
    var cur = normalize(cells)
    repeat(4) {
        if (out.none { it == cur }) out.add(cur)
        val h = cur.maxOf { it.r } + 1
        cur = normalize(cur.map { Cell(it.c, h - 1 - it.r) })
    }
    return out
}

object Shapes {
    class Family(val rotations: List<List<Cell>>, val weight: Int)

    val families = listOf(
        Family(rotations(shape("#")), 2),
        Family(rotations(shape("##")), 4),
        Family(rotations(shape("###")), 4),
        Family(rotations(shape("####")), 3),
        Family(rotations(shape("#####")), 2),
        Family(rotations(shape("##", "##")), 4),
        Family(rotations(shape("###", "###", "###")), 2),
        Family(rotations(shape("##", "##", "##")), 3),
        Family(rotations(shape("#.", "##")), 4),
        Family(rotations(shape("#..", "#..", "###")), 2),
        Family(rotations(shape("#.", "#.", "##")), 3),
        Family(rotations(shape(".#", ".#", "##")), 3),
        Family(rotations(shape("###", ".#.")), 3),
        Family(rotations(shape("##.", ".##")), 2),
        Family(rotations(shape(".##", "##.")), 2),
        // diagonals: blocks touching only at the corners (both directions come from the rotations)
        Family(rotations(shape("#.", ".#")), 3),
        Family(rotations(shape("#..", ".#.", "..#")), 2),
    )
    private val total = families.sumOf { it.weight }

    fun random(rng: Random): List<Cell> {
        var x = rng.nextInt(total)
        for (f in families) {
            x -= f.weight
            if (x < 0) return f.rotations[rng.nextInt(f.rotations.size)]
        }
        return families.last().rotations[0]
    }
}

class PlaceResult(
    val placed: List<Cell>,
    /** r, c, color of every cleared cell */
    val cleared: List<Triple<Int, Int, Int>>,
    val rows: List<Int>,
    val cols: List<Int>,
    val flushLines: Int,
    val points: Int,
    val combo: Int,
    val allClear: Boolean,
) {
    val lines get() = rows.size + cols.size
}

/**
 * Classic 8x8 block puzzle rules plus Color Flush:
 * - place a piece = 1 point per cell
 * - full rows/columns clear; L lines = 100 * L(L+1)/2, times the combo
 * - combo grows with each clearing placement and breaks after 3 placements without a clear
 * - Color Flush: a cleared line that is all one color = +400 (times combo)
 * - All clear (board empty) = +1000
 */
class Game(seed: Long = System.nanoTime()) {
    private val rng = Random(seed)
    val grid = IntArray(N * N)
    val tray = arrayOfNulls<Piece>(3)
    var score = 0; private set
    var combo = 0; private set
    var movesSinceClear = 0; private set
    var usedBlast = false
    var usedNewPieces = false
    /** Bumped every time a fresh set of 3 pieces is dealt (drives the slide-in animation). */
    var trayVersion = 0; private set

    init { newTray() }

    operator fun get(r: Int, c: Int) = grid[r * N + c]

    fun canPlace(p: Piece, r: Int, c: Int, g: IntArray = grid): Boolean {
        if (r < 0 || c < 0 || r + p.h > N || c + p.w > N) return false
        return p.cells.all { g[(r + it.r) * N + c + it.c] == 0 }
    }

    fun fitsAnywhere(p: Piece, g: IntArray = grid): Boolean {
        for (r in 0..N - p.h) for (c in 0..N - p.w) if (canPlace(p, r, c, g)) return true
        return false
    }

    fun isStuck(): Boolean = tray.filterNotNull().let { it.isNotEmpty() && it.none { p -> fitsAnywhere(p) } }

    /** Rows/cols that would be full after placing p at r,c (for the preview glow). */
    fun linesIfPlaced(p: Piece, r: Int, c: Int): Pair<List<Int>, List<Int>> {
        val g = grid.copyOf()
        p.cells.forEach { g[(r + it.r) * N + c + it.c] = p.color }
        return fullLines(g)
    }

    private fun fullLines(g: IntArray): Pair<List<Int>, List<Int>> {
        val rows = (0 until N).filter { r -> (0 until N).all { c -> g[r * N + c] != 0 } }
        val cols = (0 until N).filter { c -> (0 until N).all { r -> g[r * N + c] != 0 } }
        return rows to cols
    }

    private fun clearFull(g: IntArray) {
        val (rows, cols) = fullLines(g)
        rows.forEach { r -> for (c in 0 until N) g[r * N + c] = 0 }
        cols.forEach { c -> for (r in 0 until N) g[r * N + c] = 0 }
    }

    fun place(slot: Int, r: Int, c: Int): PlaceResult? {
        val p = tray[slot] ?: return null
        if (!canPlace(p, r, c)) return null
        val placed = p.cells.map { Cell(r + it.r, c + it.c) }
        placed.forEach { grid[it.r * N + it.c] = p.color }
        tray[slot] = null

        val (rows, cols) = fullLines(grid)
        var flush = 0
        rows.forEach { rr -> val k = grid[rr * N]; if ((0 until N).all { grid[rr * N + it] == k }) flush++ }
        cols.forEach { cc -> val k = grid[cc]; if ((0 until N).all { grid[it * N + cc] == k }) flush++ }
        val clearSet = LinkedHashSet<Int>()
        rows.forEach { rr -> for (x in 0 until N) clearSet.add(rr * N + x) }
        cols.forEach { cc -> for (y in 0 until N) clearSet.add(y * N + cc) }
        val cleared = clearSet.map { Triple(it / N, it % N, grid[it]) }
        clearSet.forEach { grid[it] = 0 }

        val lines = rows.size + cols.size
        var pts = p.cells.size
        if (lines > 0) {
            combo++
            movesSinceClear = 0
            pts += (100 * lines * (lines + 1) / 2 + 400 * flush) * combo
        } else {
            movesSinceClear++
            if (movesSinceClear >= 3) combo = 0
        }
        val allClear = lines > 0 && grid.all { it == 0 }
        if (allClear) pts += 1000
        score += pts
        if (tray.all { it == null }) newTray()
        return PlaceResult(placed, cleared, rows, cols, flush, pts, combo, allClear)
    }

    private fun randomPiece(prevColor: Int): Piece {
        // Sometimes repeat the previous color so Color Flush lines are reachable.
        val color = if (prevColor != 0 && rng.nextFloat() < 0.35f) prevColor else 1 + rng.nextInt(COLORS)
        return Piece(Shapes.random(rng), color)
    }

    /** New 3 pieces; prefers a set that can all be placed in some order. */
    fun newTray() {
        var pick: List<Piece> = emptyList()
        for (i in 0 until 40) {
            var prev = 0
            val cand = List(3) { randomPiece(prev).also { prev = it.color } }
            pick = cand
            if (solvable(cand)) break
        }
        for (i in 0 until 3) tray[i] = pick[i]
        trayVersion++
        if (isStuck() && grid.any { it == 0 }) tray[0] = Piece(listOf(Cell(0, 0)), 1 + rng.nextInt(COLORS))
    }

    private fun solvable(pieces: List<Piece>): Boolean {
        val perms = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        for (perm in perms) {
            val g = grid.copyOf()
            var ok = true
            for (i in perm) {
                val p = pieces[i]
                var done = false
                loop@ for (r in 0..N - p.h) for (c in 0..N - p.w) if (canPlace(p, r, c, g)) {
                    p.cells.forEach { g[(r + it.r) * N + c + it.c] = p.color }
                    clearFull(g)
                    done = true
                    break@loop
                }
                if (!done) { ok = false; break }
            }
            if (ok) return true
        }
        return false
    }

    /** Continue offer: clears the fullest 3x3 area. */
    fun blast3x3(): List<Triple<Int, Int, Int>> {
        var best = 0; var br = 0; var bc = 0
        for (r in 0..N - 3) for (c in 0..N - 3) {
            var n = 0
            for (y in r until r + 3) for (x in c until c + 3) if (grid[y * N + x] != 0) n++
            if (n > best) { best = n; br = r; bc = c }
        }
        val out = mutableListOf<Triple<Int, Int, Int>>()
        for (y in br until br + 3) for (x in bc until bc + 3) {
            val k = grid[y * N + x]
            if (k != 0) { out.add(Triple(y, x, k)); grid[y * N + x] = 0 }
        }
        if (isStuck()) newTray()
        return out
    }

    /**
     * Journey: scatter random pieces on the board (about [cells] blocks) so a level starts messy.
     * Never makes a full line, then deals a tray that fits the new board.
     */
    fun seed(cells: Int) {
        var filled = 0
        var tries = 0
        while (filled < cells && tries < 500) {
            tries++
            val p = Piece(Shapes.random(rng), 1 + rng.nextInt(COLORS))
            val r = rng.nextInt(N - p.h + 1)
            val c = rng.nextInt(N - p.w + 1)
            if (!canPlace(p, r, c)) continue
            val g = grid.copyOf()
            p.cells.forEach { g[(r + it.r) * N + c + it.c] = p.color }
            val (rows, cols) = fullLines(g)
            if (rows.isNotEmpty() || cols.isNotEmpty()) continue
            g.copyInto(grid)
            filled += p.cells.size
        }
        newTray()
    }

    /** Debug only (store screenshots): set the board, score, combo and tray directly. */
    fun debugSet(cells: IntArray, score: Int, combo: Int, pieces: List<Piece?>) {
        cells.copyInto(grid)
        this.score = score; this.combo = combo; movesSinceClear = 0
        pieces.forEachIndexed { i, p -> tray[i] = p }
        trayVersion++
    }

    /** Debug only (store screenshots). */
    fun debugScore(s: Int) { score = s }

    /** Hammer booster: smash one block. Returns its color, or 0. */
    fun hammer(r: Int, c: Int): Int {
        val k = grid[r * N + c]
        grid[r * N + c] = 0
        return k
    }

    fun rotate(slot: Int) { tray[slot] = tray[slot]?.rotated() }

    /** True when there is a game worth continuing (something placed, and not stuck). */
    fun inProgress(): Boolean = (score > 0 || grid.any { it != 0 }) && !isStuck()

    /**
     * Compact save string: score;combo;movesSinceClear;usedBlast;usedNewPieces;64 grid digits;3 tray pieces.
     * A piece is "color:r.c r.c ..." and an empty slot is "-".
     */
    fun save(): String {
        val pieces = tray.joinToString("|") { p ->
            if (p == null) "-" else p.color.toString() + ":" + p.cells.joinToString(" ") { "${it.r}.${it.c}" }
        }
        return listOf(score, combo, movesSinceClear, if (usedBlast) 1 else 0, if (usedNewPieces) 1 else 0,
            grid.joinToString(""), pieces).joinToString(";")
    }

    /** Restores a [save] string. Returns false (and changes nothing) if it is missing or malformed. */
    fun load(s: String?): Boolean {
        if (s.isNullOrBlank()) return false
        return try {
            val f = s.split(";")
            if (f.size != 7 || f[5].length != N * N) return false
            val g = IntArray(N * N) { f[5][it].digitToInt().also { k -> require(k in 0..COLORS) } }
            val t = f[6].split("|").also { require(it.size == 3) }.map { ps ->
                if (ps == "-") null else {
                    val (col, cells) = ps.split(":")
                    Piece(normalize(cells.split(" ").map { rc -> rc.split(".").let { Cell(it[0].toInt(), it[1].toInt()) } }), col.toInt())
                }
            }
            score = f[0].toInt(); combo = f[1].toInt(); movesSinceClear = f[2].toInt()
            usedBlast = f[3] == "1"; usedNewPieces = f[4] == "1"
            g.copyInto(grid)
            t.forEachIndexed { i, p -> tray[i] = p }
            if (tray.all { it == null }) newTray() else trayVersion++
            true
        } catch (e: Exception) {
            false
        }
    }

    fun reset() {
        grid.fill(0)
        score = 0; combo = 0; movesSinceClear = 0
        usedBlast = false; usedNewPieces = false
        newTray()
    }
}
