package com.playerreinforcements.config;

import java.io.File;
import java.lang.reflect.Field;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ReinforcementsConfigTest {
    private static Field minecraftHome;
    private static Object originalMinecraftHome;

    @BeforeClass
    public static void provideTheGameDirectoryNormallySetByForge() throws Exception {
        minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        originalMinecraftHome = minecraftHome.get(null);
        if (originalMinecraftHome == null) {
            minecraftHome.set(null, new File(System.getProperty("java.io.tmpdir")));
        }
    }

    @AfterClass
    public static void restoreTheForgeTestEnvironment() throws Exception {
        minecraftHome.set(null, originalMinecraftHome);
    }

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @After
    public void restoreDefaults() throws Exception {
        ReinforcementsConfig.load(new File(temporary.newFolder(), "defaults.cfg"));
    }

    @Test
    public void teammateDimensionsAreHiddenWhenTheOptionIsMissing() throws Exception {
        File file = new File(temporary.newFolder(), "playerreinforcements.cfg");
        ReinforcementsConfig.load(file);
        assertFalse(ReinforcementsConfig.showTeammateDimensions());
        Configuration saved = new Configuration(file);
        saved.load();
        assertFalse(saved.get("general", "showTeammateDimensions", true).getBoolean());
    }

    @Test
    public void teammateDimensionDisplayCanBeExplicitlyEnabled() throws Exception {
        File file = new File(temporary.newFolder(), "playerreinforcements.cfg");
        Configuration enabled = new Configuration(file);
        enabled.get("general", "showTeammateDimensions", false).set(true);
        enabled.save();
        ReinforcementsConfig.load(file);
        assertTrue(ReinforcementsConfig.showTeammateDimensions());
    }

    @Test
    public void testDummiesAreOffAndMinigamesAreOnByDefaultWithoutTimerRewards() throws Exception {
        File file = new File(temporary.newFolder(), "playerreinforcements.cfg");
        ReinforcementsConfig.load(file);
        assertFalse(ReinforcementsConfig.testDummies());
        assertTrue(ReinforcementsConfig.enableDeathMinigames());
        assertFalse(ReinforcementsConfig.minigameReducesRespawnTimer());
        Configuration saved = new Configuration(file);
        saved.load();
        assertFalse(saved.get("general", "testDummies", true).getBoolean());
        assertTrue(saved.get("general", "enableDeathMinigames", false).getBoolean());
        assertFalse(saved.get("general", "minigameReducesRespawnTimer", true).getBoolean());
    }

    @Test
    public void testDummiesCanBeEnabledAndMinigamesDisabledWithTimerRewards() throws Exception {
        File file = new File(temporary.newFolder(), "playerreinforcements.cfg");
        Configuration changed = new Configuration(file);
        changed.get("general", "testDummies", false).set(true);
        changed.get("general", "enableDeathMinigames", true).set(false);
        changed.get("general", "minigameReducesRespawnTimer", false).set(true);
        changed.save();
        ReinforcementsConfig.load(file);
        assertTrue(ReinforcementsConfig.testDummies());
        assertFalse(ReinforcementsConfig.enableDeathMinigames());
        assertTrue(ReinforcementsConfig.minigameReducesRespawnTimer());
    }

    @Test
    public void documentedDefaultsAreSupported() {
        ReinforcementsConfig.validate(false, false, true, false, "GROUP_RESPAWN", 2, "MINUTES");
    }

    @Test
    public void everyNonDefaultOptionReportsNotImplemented() {
        reject(() -> ReinforcementsConfig.validate(true, false, true, false, "GROUP_RESPAWN", 2, "MINUTES"), "enableSpectate");
        reject(() -> ReinforcementsConfig.validate(false, true, true, false, "GROUP_RESPAWN", 2, "MINUTES"), "enableStratagems");
        reject(() -> ReinforcementsConfig.validate(false, false, false, false, "GROUP_RESPAWN", 2, "MINUTES"), "enableRespawnTimer");
        reject(() -> ReinforcementsConfig.validate(false, false, true, true, "GROUP_RESPAWN", 2, "MINUTES"), "enableRespawningOnSpawnpoint");
        reject(() -> ReinforcementsConfig.validate(false, false, true, false, "SPAWN_ON_PLAYER", 2, "MINUTES"), "respawnMode");
        reject(() -> ReinforcementsConfig.validate(false, false, true, false, "UNKNOWN", 2, "MINUTES"), "respawnMode");
        reject(() -> ReinforcementsConfig.validate(false, false, true, false, "GROUP_RESPAWN", 0, "MINUTES"), "respawnCooldown");
        reject(() -> ReinforcementsConfig.validate(false, false, true, false, "GROUP_RESPAWN", 120, "SECONDS"), "respawnCooldown");
        reject(() -> ReinforcementsConfig.validate(false, false, true, false, "GROUP_RESPAWN", 2, "TICKS"), "respawnCooldownUnit");
    }

    private void reject(Runnable action, String setting) {
        try {
            action.run();
            fail("Expected an unsupported-setting error for " + setting);
        } catch (IllegalArgumentException exception) {
            assertEquals("error - not implemented yet: " + setting, exception.getMessage());
        }
    }
}
