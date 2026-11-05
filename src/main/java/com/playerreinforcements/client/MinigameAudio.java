package com.playerreinforcements.client;

import com.playerreinforcements.minigame.MinigameSession;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class MinigameAudio {
    private static final Field VANILLA_MUSIC = ReflectionHelper.findField(MusicTicker.class, "currentMusic", "field_147678_c");
    private static final long INPUT_WINDOW_NS = 500_000_000L;
    private static final long MOVE_SOUND_INTERVAL_NS = 85_000_000L;
    private static final long SOUNDTRACK_CHECK_INTERVAL_NS = 1_000_000_000L;
    private MinigameSession previous;
    private ISound soundtrack;
    private int soundtrackGame = -1;
    private long nextSoundtrackCheck;
    private long moveUntil;
    private long dropUntil;
    private long nextMoveSound;

    void click(Minecraft mc) {
        play(mc, SoundEvents.UI_BUTTON_CLICK, 1.0F, 0.25F);
    }

    void startRequested() {
        previous = null;
        moveUntil = dropUntil = 0;
    }

    void input(Minecraft mc, MinigameSession session, int controls) {
        if (session == null || session.gameOver()) return;
        if (session.type() == MinigameSession.FLAPPY) {
            if ((controls & MinigameSession.FLAP) != 0) play(mc, SoundEvents.ENTITY_BAT_TAKEOFF, 1.2F, 0.28F);
        } else {
            long expires = System.nanoTime() + INPUT_WINDOW_NS;
            if ((controls & (MinigameSession.LEFT | MinigameSession.RIGHT | MinigameSession.ROTATE)) != 0) {
                moveUntil = expires;
            }
            if ((controls & (MinigameSession.SOFT_DROP | MinigameSession.HARD_DROP)) != 0) {
                dropUntil = expires;
            }
        }
    }

    void update(Minecraft mc, MinigameSession session) {
        if (session != null && previous != null && session != previous
                && previous.type() == session.type() && !previous.gameOver()) {
            int earned = session.score() - previous.score();
            if (earned >= 10) {
                moveUntil = dropUntil = 0;
                float pitch = 1.05F + Math.min(0.3F, (earned - 10) / 100.0F);
                play(mc, SoundEvents.ENTITY_PLAYER_LEVELUP, pitch, 0.45F);
            } else if (earned > 0) {
                play(mc, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2F, 0.55F);
            } else if (earned == 0 && session.type() == MinigameSession.TETRIS) {
                playTetrisMove(mc, session);
            }
            if (session.gameOver()) {
                play(mc, SoundEvents.BLOCK_NOTE_BASS, 0.7F, 0.55F);
                mc.getSoundHandler().playDelayedSound(
                        PositionedSoundRecord.getRecord(SoundEvents.BLOCK_NOTE_BASS, 0.5F, 0.55F), 4);
            }
        }
        previous = session;

        int activeGame = session == null || session.gameOver() ? -1 : session.type();
        if (activeGame == soundtrackGame) {
            if (soundtrack != null && System.nanoTime() - nextSoundtrackCheck >= 0) {
                nextSoundtrackCheck = System.nanoTime() + SOUNDTRACK_CHECK_INTERVAL_NS;
                if (mc.gameSettings.getSoundLevel(SoundCategory.MASTER) > 0
                        && mc.gameSettings.getSoundLevel(SoundCategory.MUSIC) > 0
                        && !mc.getSoundHandler().isSoundPlaying(soundtrack)) {
                    mc.getSoundHandler().playSound(soundtrack);
                }
            }
            return;
        }
        stopSoundtrack(mc);
        if (activeGame >= 0) {
            SoundEvent disc = activeGame == MinigameSession.TETRIS ? SoundEvents.RECORD_BLOCKS : SoundEvents.RECORD_CAT;
            ISound ambient = vanillaMusic(mc);
            if (ambient != null) mc.getSoundHandler().stopSound(ambient);
            float volume = activeGame == MinigameSession.TETRIS ? 0.48F : 0.4F;
            soundtrack = new PositionedSoundRecord(disc.getSoundName(), SoundCategory.MUSIC, volume, 1.0F,
                    true, 0, ISound.AttenuationType.NONE, 0, 0, 0);
            soundtrackGame = activeGame;
            nextSoundtrackCheck = System.nanoTime() + SOUNDTRACK_CHECK_INTERVAL_NS;
            mc.getSoundHandler().playSound(soundtrack);
        }
    }

    private void playTetrisMove(Minecraft mc, MinigameSession session) {
        long now = System.nanoTime();
        boolean shifted = moveUntil != 0 && moveUntil - now > 0
                && (session.pieceX() != previous.pieceX() || session.rotation() != previous.rotation());
        boolean dropped = dropUntil != 0 && dropUntil - now > 0
                && (session.pieceY() != previous.pieceY() || session.piece() != previous.piece()
                    || session.nextPiece() != previous.nextPiece() || session.gameOver());
        if (shifted) moveUntil = 0;
        if (dropped) dropUntil = 0;
        if ((shifted || dropped) && (nextMoveSound == 0 || now - nextMoveSound >= 0)) {
            nextMoveSound = now + MOVE_SOUND_INTERVAL_NS;
            play(mc, SoundEvents.BLOCK_NOTE_HAT, dropped ? 1.0F : 1.45F, 0.3F);
        }
    }

    boolean replacesVanillaMusic(Minecraft mc, ISound sound) {
        return soundtrack != null && sound.getCategory() == SoundCategory.MUSIC && sound == vanillaMusic(mc);
    }

    void stop(Minecraft mc) {
        stopSoundtrack(mc);
        startRequested();
        nextMoveSound = 0;
    }

    private void stopSoundtrack(Minecraft mc) {
        if (soundtrack != null) mc.getSoundHandler().stopSound(soundtrack);
        soundtrack = null;
        soundtrackGame = -1;
        nextSoundtrackCheck = 0;
    }

    private static ISound vanillaMusic(Minecraft mc) {
        try {
            return (ISound) VANILLA_MUSIC.get(mc.getMusicTicker());
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not read the vanilla music track", failure);
        }
    }

    private static void play(Minecraft mc, SoundEvent event, float pitch, float volume) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(event, pitch, volume));
    }
}
