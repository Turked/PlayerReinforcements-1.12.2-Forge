package com.playerreinforcements.minigame;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.*;

public final class MinigameSessionTest {
    @Test
    public void cartAndPillarsWaitForFirstFlap() {
        MinigameSession game = new MinigameSession(MinigameSession.FLAPPY, 123);
        float initialY = game.cartY();
        float initialPillar = game.pillarX(0);
        for (int tick = 0; tick < 200; tick++) assertFalse(game.tick(0));
        assertFalse(game.flappyStarted());
        assertFalse(game.gameOver());
        assertEquals(initialY, game.cartY(), 0);
        assertEquals(initialPillar, game.pillarX(0), 0);

        assertTrue(game.tick(MinigameSession.FLAP));
        assertTrue(game.flappyStarted());
        assertTrue(game.cartY() < initialY);
        assertTrue(game.pillarX(0) < initialPillar);
        for (int tick = 0; tick < 200; tick++) game.tick(0);
        assertTrue(game.gameOver());
        assertEquals(0, game.score());
        float stoppedY = game.cartY();
        assertFalse(game.tick(MinigameSession.FLAP));
        assertEquals(stoppedY, game.cartY(), 0);
    }

    @Test
    public void clearingAnObstacleAwardsExactlyOnePoint() {
        MinigameSession game = new MinigameSession(MinigameSession.FLAPPY, 123);
        game.tick(MinigameSession.FLAP);
        for (int tick = 0; tick < 110 && game.score() == 0 && !game.gameOver(); tick++) {
            int target = game.gapY(0) + 33;
            game.tick(game.cartY() > target ? MinigameSession.FLAP : 0);
        }
        assertFalse(game.gameOver());
        assertEquals(1, game.score());
        for (int tick = 0; tick < 3; tick++) game.tick(0);
        assertEquals(1, game.score());
    }

    @Test
    public void hardDropClearsLineAndOnlyLinesAwardPoints() {
        byte[][] board = new byte[20][10];
        for (int x = 0; x < 10; x++) if (x < 3 || x > 6) board[19][x] = 2;
        MinigameSession game = tetris(board, 0, 0, 3, 0);
        game.tick(MinigameSession.HARD_DROP);
        assertEquals(10, game.score());
        for (int x = 0; x < 10; x++) assertEquals(0, game.cell(x, 19));
        game.tick(MinigameSession.HARD_DROP);
        assertEquals(10, game.score());
        assertFalse(game.gameOver());
    }

    @Test
    public void simultaneousFourLineClearScoresFortyAndCompactsBoard() {
        byte[][] board = new byte[20][10];
        board[15][0] = 3;
        for (int y = 16; y < 20; y++) {
            for (int x = 0; x < 10; x++) if (x != 5) board[y][x] = 2;
        }
        MinigameSession game = tetris(board, 0, 1, 3, 0);
        game.tick(MinigameSession.HARD_DROP);
        assertEquals(40, game.score());
        assertEquals(3, game.cell(0, 19));
        for (int x = 1; x < 10; x++) assertEquals(0, game.cell(x, 19));
    }

    @Test
    public void cannotMoveThroughWallOrDropThroughOccupiedCells() {
        byte[][] board = new byte[20][10];
        board[19][1] = 3;
        MinigameSession game = tetris(board, 1, 0, -1, 0);
        assertFalse(game.tick(MinigameSession.LEFT));
        assertEquals(-1, game.pieceX());
        game.tick(MinigameSession.HARD_DROP);
        assertEquals(2, game.cell(0, 17));
        assertEquals(2, game.cell(1, 18));
        assertEquals(3, game.cell(1, 19));
    }

    @Test
    public void tetrisReportsMovementAndGravityButSkipsUnchangedTicks() {
        MinigameSession game = tetris(new byte[20][10], 1, 0, 3, 0);
        assertTrue(game.tick(MinigameSession.LEFT));
        assertFalse(game.tick(MinigameSession.ROTATE));
        for (int tick = 2; tick < 7; tick++) assertFalse(game.tick(0));
        assertEquals(0, game.pieceY());
        assertTrue(game.tick(0));
        assertEquals(1, game.pieceY());
        assertTrue(game.tick(MinigameSession.HARD_DROP));
    }

    @Test
    public void blockedSpawnEndsGameAndNewGameStartsClean() {
        byte[][] board = new byte[20][10];
        board[0][4] = 2;
        MinigameSession game = tetris(board, 0, 0, 3, 17);
        game.tick(MinigameSession.HARD_DROP);
        assertTrue(game.gameOver());
        MinigameSession restarted = new MinigameSession(MinigameSession.TETRIS, 5);
        assertFalse(restarted.gameOver());
        assertEquals(0, restarted.score());
        assertEquals(0, restarted.cell(4, 0));
    }

    @Test
    public void snapshotsPreserveVisibleStateForBothGames() {
        for (int type = 0; type <= 1; type++) {
            MinigameSession original = new MinigameSession(type, 33);
            for (int tick = 0; tick < 6; tick++) original.tick(type == 0 ? MinigameSession.FLAP : MinigameSession.RIGHT);
            ByteBuf buffer = Unpooled.buffer();
            try {
                original.write(buffer);
                MinigameSession decoded = MinigameSession.read(buffer);
                assertEquals(original.type(), decoded.type());
                assertEquals(original.score(), decoded.score());
                assertEquals(original.gameOver(), decoded.gameOver());
                if (type == MinigameSession.FLAPPY) {
                    assertEquals(original.flappyStarted(), decoded.flappyStarted());
                    assertEquals(original.cartY(), decoded.cartY(), 0);
                    assertEquals(original.cartVelocity(), decoded.cartVelocity(), 0);
                    assertEquals(original.pillarX(0), decoded.pillarX(0), 0);
                    assertEquals(original.gapY(0), decoded.gapY(0));
                } else {
                    assertEquals(original.pieceX(), decoded.pieceX());
                    assertEquals(original.pieceY(), decoded.pieceY());
                    assertEquals(original.piece(), decoded.piece());
                }
                assertEquals(0, buffer.readableBytes());
            } finally {
                buffer.release();
            }
        }
    }

    private static MinigameSession tetris(byte[][] board, int piece, int rotation, int x, int y) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            buffer.writeByte(MinigameSession.TETRIS);
            buffer.writeInt(0);
            buffer.writeBoolean(false);
            for (byte[] row : board) buffer.writeBytes(row);
            buffer.writeByte(piece);
            buffer.writeByte(1);
            buffer.writeByte(rotation);
            buffer.writeByte(x);
            buffer.writeByte(y);
            return MinigameSession.read(buffer);
        } finally {
            buffer.release();
        }
    }
}
