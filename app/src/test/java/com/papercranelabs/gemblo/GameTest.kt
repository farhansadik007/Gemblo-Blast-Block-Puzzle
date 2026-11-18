package com.papercranelabs.gemblo

import com.papercranelabs.gemblo.game.Cell
import com.papercranelabs.gemblo.game.Game
import com.papercranelabs.gemblo.game.N
import com.papercranelabs.gemblo.game.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameTest {
    private fun bar(len: Int, color: Int) = Piece(List(len) { Cell(0, it) }, color)

    @Test fun rowClearScoresAndEmpties() {
        val g = Game(1)
        for (c in 0 until 7) g.grid[c] = 2          // row 0: 7 cells, last empty
        g.grid[5 * N + 3] = 1                       // keeps the board from being all clear
        g.tray[0] = Piece(listOf(Cell(0, 0)), 3)    // different color -> no flush
        val res = g.place(0, 0, 7)!!
        assertEquals(1, res.lines)
        assertEquals(0, res.flushLines)
        assertEquals(1 + 100, res.points)
        assertTrue((0 until N).all { g[0, it] == 0 })
    }

    @Test fun colorFlushBonus() {
        val g = Game(2)
        for (c in 0 until 4) g.grid[c] = 4
        g.tray[0] = bar(4, 4)
        val res = g.place(0, 0, 4)!!
        assertEquals(1, res.flushLines)
        assertTrue(res.allClear)
        assertEquals(4 + 100 + 400 + 1000, res.points)
    }

    @Test fun comboBreaksAfterThreeMisses() {
        val g = Game(3)
        for (c in 0 until 7) g.grid[c] = 1
        g.tray[0] = Piece(listOf(Cell(0, 0)), 2)
        g.place(0, 0, 7)
        assertEquals(1, g.combo)
        repeat(3) { i ->
            g.tray[0] = Piece(listOf(Cell(0, 0)), 2)
            g.place(0, 7, i)
        }
        assertEquals(0, g.combo)
    }

    @Test fun cannotPlaceOverBlocksOrOutside() {
        val g = Game(4)
        g.grid[0] = 1
        assertFalse(g.canPlace(bar(2, 1), 0, 0))
        assertFalse(g.canPlace(bar(2, 1), 0, 7))
        assertTrue(g.canPlace(bar(2, 1), 0, 1))
    }

    @Test fun rotationsKeepCellCount() {
        val l = Piece(listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1)), 1)
        val r = l.rotated()
        assertEquals(4, r.cells.size)
        assertEquals(3, r.w)
        assertEquals(2, r.h)
    }
}
