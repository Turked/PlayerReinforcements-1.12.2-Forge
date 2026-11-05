package com.playerreinforcements.minigame;

import io.netty.buffer.ByteBuf;
import java.util.Random;

/** Server-owned rules; snapshots contain only the state needed to draw the game. */
public final class MinigameSession {
    public static final int FLAPPY = 0;
    public static final int TETRIS = 1;
    public static final int FLAP = 1;
    public static final int LEFT = 2;
    public static final int RIGHT = 4;
    public static final int ROTATE = 8;
    public static final int SOFT_DROP = 16;
    public static final int HARD_DROP = 32;
    public static final int ALL_CONTROLS = 63;
    public static final int COLUMNS = 10;
    public static final int ROWS = 20;
    public static final int FLAPPY_WIDTH = 160;
    public static final int FLAPPY_HEIGHT = 180;
    public static final int CART_X = 34;
    public static final int CART_WIDTH = 16;
    public static final int CART_HEIGHT = 16;
    public static final int PILLAR_WIDTH = 20;
    public static final int GAP_HEIGHT = 64;
    public static final int PILLAR_COUNT = 3;
    public static final float PILLAR_SPEED = 3.0F;
    public static final float FLAP_VELOCITY = -4.2F;
    private static final float GRAVITY = 0.44F;
    private static final int PILLAR_SPACING = 100;
    private static final int FIRST_PILLAR = FLAPPY_WIDTH - 12;
    private static final int POINTS_PER_LINE = 10;

    private static final int[][][] SHAPES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{1, 0}, {2, 0}, {1, 1}, {2, 1}},
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}
    };

    private final int type;
    private final Random random;
    private int score;
    private boolean gameOver;
    private boolean flappyStarted;
    private float cartY = (FLAPPY_HEIGHT - CART_HEIGHT) / 2.0F;
    private float velocity;
    private final float[] pillars = new float[PILLAR_COUNT];
    private final int[] gaps = new int[PILLAR_COUNT];
    private final boolean[] passed = new boolean[PILLAR_COUNT];
    private int previousGap = (FLAPPY_HEIGHT - GAP_HEIGHT) / 2;
    private final byte[][] board = new byte[ROWS][COLUMNS];
    private int piece;
    private int nextPiece;
    private int rotation;
    private int pieceX = 3;
    private int pieceY;
    private int dropTicks;
    private final int[] bag = new int[7];
    private int bagIndex = 7;

    public MinigameSession(int type, long seed) {
        if (type != FLAPPY && type != TETRIS) throw new IllegalArgumentException("Unknown minigame");
        this.type = type;
        random = new Random(seed);
        for (int i = 0; i < gaps.length; i++) {
            pillars[i] = FIRST_PILLAR + i * PILLAR_SPACING;
            gaps[i] = nextGap();
        }
        piece = nextFromBag();
        nextPiece = nextFromBag();
    }

    public int type() { return type; }
    public int score() { return score; }
    public boolean gameOver() { return gameOver; }
    public boolean flappyStarted() { return flappyStarted; }
    public float cartY() { return cartY; }
    public float cartVelocity() { return velocity; }
    public float pillarX(int index) { return pillars[index]; }
    public int gapY(int index) { return gaps[index]; }
    public int cell(int x, int y) { return board[y][x]; }
    public int piece() { return piece; }
    public int nextPiece() { return nextPiece; }
    public int pieceX() { return pieceX; }
    public int pieceY() { return pieceY; }
    public int rotation() { return rotation; }
    public int blockX(int index) { return rotatedX(piece, rotation, index); }
    public int blockY(int index) { return rotatedY(piece, rotation, index); }

    /** Advances one tick and reports whether clients need a new rendering snapshot. */
    public boolean tick(int controls) {
        if (gameOver) return false;
        return type == FLAPPY ? tickFlappy(controls) : tickTetris(controls);
    }

    private int nextGap() {
        // Keep adjacent gaps close enough to reach at full scroll speed.
        previousGap = Math.max(20, Math.min(FLAPPY_HEIGHT - GAP_HEIGHT - 20,
                previousGap + random.nextInt(49) - 24));
        return previousGap;
    }

    private boolean tickFlappy(int controls) {
        boolean flap = (controls & FLAP) != 0;
        if (!flappyStarted && !flap) return false;
        flappyStarted = true;
        velocity = flap ? FLAP_VELOCITY : Math.min(7.5F, velocity + GRAVITY);
        cartY += velocity;
        if (cartY < 0 || cartY + CART_HEIGHT > FLAPPY_HEIGHT) {
            cartY = Math.max(0, Math.min(FLAPPY_HEIGHT - CART_HEIGHT, cartY));
            gameOver = true;
        }
        for (int i = 0; i < pillars.length; i++) {
            pillars[i] -= PILLAR_SPEED;
            if (pillars[i] + PILLAR_WIDTH < 0) {
                pillars[i] += PILLAR_SPACING * PILLAR_COUNT;
                gaps[i] = nextGap();
                passed[i] = false;
            }
            if (CART_X + CART_WIDTH > pillars[i] && CART_X < pillars[i] + PILLAR_WIDTH
                    && (cartY < gaps[i] || cartY + CART_HEIGHT > gaps[i] + GAP_HEIGHT)) gameOver = true;
            if (!passed[i] && pillars[i] + PILLAR_WIDTH < CART_X && !gameOver) {
                passed[i] = true;
                score++;
            }
        }
        return true;
    }

    private int nextFromBag() {
        if (bagIndex == bag.length) {
            for (int i = 0; i < bag.length; i++) bag[i] = i;
            for (int i = bag.length - 1; i > 0; i--) {
                int swap = random.nextInt(i + 1);
                int previous = bag[i];
                bag[i] = bag[swap];
                bag[swap] = previous;
            }
            bagIndex = 0;
        }
        return bag[bagIndex++];
    }

    private static int rotatedX(int piece, int rotation, int block) {
        int x = SHAPES[piece][block][0];
        int y = SHAPES[piece][block][1];
        if (piece == 1) return x;
        int size = piece == 0 ? 4 : 3;
        for (int i = 0; i < rotation; i++) {
            int oldX = x;
            x = size - 1 - y;
            y = oldX;
        }
        return x;
    }

    private static int rotatedY(int piece, int rotation, int block) {
        int x = SHAPES[piece][block][0];
        int y = SHAPES[piece][block][1];
        if (piece == 1) return y;
        int size = piece == 0 ? 4 : 3;
        for (int i = 0; i < rotation; i++) {
            int oldX = x;
            x = size - 1 - y;
            y = oldX;
        }
        return y;
    }

    private boolean fits(int x, int y, int angle) {
        for (int block = 0; block < 4; block++) {
            int bx = x + rotatedX(piece, angle, block);
            int by = y + rotatedY(piece, angle, block);
            if (bx < 0 || bx >= COLUMNS || by < 0 || by >= ROWS || board[by][bx] != 0) return false;
        }
        return true;
    }

    private boolean tickTetris(int controls) {
        int oldX = pieceX;
        int oldY = pieceY;
        int oldRotation = rotation;
        if ((controls & LEFT) != 0 && (controls & RIGHT) == 0 && fits(pieceX - 1, pieceY, rotation)) pieceX--;
        if ((controls & RIGHT) != 0 && (controls & LEFT) == 0 && fits(pieceX + 1, pieceY, rotation)) pieceX++;
        if ((controls & ROTATE) != 0 && piece != 1) {
            int rotated = (rotation + 1) % 4;
            int[] kicks = {0, -1, 1, -2, 2};
            for (int kick : kicks) {
                if (fits(pieceX + kick, pieceY, rotated)) {
                    pieceX += kick;
                    rotation = rotated;
                    break;
                }
            }
        }
        if ((controls & HARD_DROP) != 0) {
            while (fits(pieceX, pieceY + 1, rotation)) pieceY++;
            lockPiece();
            return true;
        }
        dropTicks++;
        boolean softDrop = (controls & SOFT_DROP) != 0;
        if (softDrop || dropTicks >= Math.max(3, 8 - score / POINTS_PER_LINE / 5)) {
            dropTicks = 0;
            if (fits(pieceX, pieceY + 1, rotation)) {
                pieceY++;
                if (softDrop && fits(pieceX, pieceY + 1, rotation)) pieceY++;
            } else {
                lockPiece();
                return true;
            }
        }
        return pieceX != oldX || pieceY != oldY || rotation != oldRotation;
    }

    private void lockPiece() {
        for (int block = 0; block < 4; block++) {
            board[pieceY + blockY(block)][pieceX + blockX(block)] = (byte) (piece + 1);
        }
        for (int row = ROWS - 1; row >= 0; row--) {
            boolean full = true;
            for (int x = 0; x < COLUMNS; x++) full &= board[row][x] != 0;
            if (full) {
                for (int y = row; y > 0; y--) System.arraycopy(board[y - 1], 0, board[y], 0, COLUMNS);
                for (int x = 0; x < COLUMNS; x++) board[0][x] = 0;
                score += POINTS_PER_LINE;
                row++;
            }
        }
        piece = nextPiece;
        nextPiece = nextFromBag();
        pieceX = 3;
        pieceY = rotation = dropTicks = 0;
        gameOver = !fits(pieceX, pieceY, rotation);
    }

    public void write(ByteBuf buf) {
        buf.writeByte(type);
        buf.writeInt(score);
        buf.writeBoolean(gameOver);
        if (type == FLAPPY) {
            buf.writeBoolean(flappyStarted);
            buf.writeFloat(cartY);
            buf.writeFloat(velocity);
            for (int i = 0; i < pillars.length; i++) {
                buf.writeFloat(pillars[i]);
                buf.writeByte(gaps[i]);
            }
        } else {
            for (byte[] row : board) buf.writeBytes(row);
            buf.writeByte(piece);
            buf.writeByte(nextPiece);
            buf.writeByte(rotation);
            buf.writeByte(pieceX);
            buf.writeByte(pieceY);
        }
    }

    /** Read-only rendering snapshot: a client never supplies this state to the server. */
    public static MinigameSession read(ByteBuf buf) {
        MinigameSession result = new MinigameSession(buf.readUnsignedByte(), 0L);
        result.score = Math.max(0, buf.readInt());
        result.gameOver = buf.readBoolean();
        if (result.type == FLAPPY) {
            result.flappyStarted = buf.readBoolean();
            result.cartY = buf.readFloat();
            result.velocity = buf.readFloat();
            for (int i = 0; i < result.pillars.length; i++) {
                result.pillars[i] = buf.readFloat();
                result.gaps[i] = buf.readUnsignedByte();
            }
        } else {
            for (byte[] row : result.board) buf.readBytes(row);
            result.piece = Math.min(6, buf.readUnsignedByte());
            result.nextPiece = Math.min(6, buf.readUnsignedByte());
            result.rotation = buf.readUnsignedByte() % 4;
            result.pieceX = buf.readByte();
            result.pieceY = buf.readByte();
        }
        return result;
    }
}
