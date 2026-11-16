Player Reinforcements 1.0.1
Minecraft 1.12.2 / Forge 14.23.5.2860 / RLCraft 2.9.3

Install
-------
Build with Java 8 using gradlew.bat build. Install the resulting
build/libs/playerreinforcements-1.0.1.jar in the mods folder on the server
and on every client. This mod adds a networked death screen and is required
on both sides. Update the server and every client to the same version.
Keep the RLCraft modpack's existing mods and configuration.

Default behavior
----------------
All online, non-spectator players form one group. Normal death processing,
item drops, and Forge clone/respawn hooks still run.

On a player's first join, group mode places them safely beside an existing
living real player. The first player uses the normal world spawn. Returning
players keep their saved location. Existing world player files are recognized.

After you die, a usable personal spawnpoint sends you there immediately.
Beds, waystones, and command-set spawnpoints follow Forge/RLCraft's normal
respawn rules. If your personal spawnpoint becomes unusable and the game
would use world spawn, teammate respawning becomes available again.

If the game would use world spawn and a teammate is alive, wait two minutes
(2400 server ticks), then click their name on the death screen to respawn
nearby. The server checks the cooldown and the teammate's current state.
The screen uses Minecraft's font, buttons, death-screen gradient, and GUI
scaling. Scroll long teammate lists with the scrollbar, mouse wheel,
Up/Down, Page Up/Page Down, or Home/End.
Teammate dimensions are hidden by default. Every teammate button includes
their skin's face and hat icon, with a default Steve/Alex face while loading.

Test dummies are disabled by default. Set testDummies=true to spawn three named
survival player actors near the first player. Dummy_Still stays put,
Dummy_Follow follows that player, and Dummy_Runner runs toward random
destinations. They use normal collisions,
can take damage, and hostile mobs can target them as players. They count as
living teammates and can be selected as respawn targets, so solo players can
test the two-minute countdown and teammate respawning. There are three per
server; they do not join the real connection list or persist as saved players.
Dead dummies stay dead until the group wipes; then all three return beside the
group's normal respawn destination. Set testDummies=false for normal solo play.

If no teammates are alive, players who would use world spawn skip the
cooldown and respawn together. One player's normal Forge/RLCraft world-spawn
fallback sets the shared destination, including RLCraft's random respawn
radius. Anyone with a usable personal spawnpoint respawns there. Respawning
near a teammate can take you to their dimension.

Waiting state and cooldown deadlines are saved in the overworld's data.
Disconnecting does not clear the state or restart the cooldown. Time
continues while the server/world ticks and pauses while the server is off.
Offline players are not counted as living teammates and do not delay an
online group's respawn; they can rejoin the group after reconnecting.

Configuration
-------------
config/playerreinforcements.cfg is created on first launch:

  enableSpectate = false
  enableStratagems = false
  enableRespawnTimer = true
  enableRespawningOnSpawnpoint = false
  showTeammateDimensions = false
  testDummies = false
  enableDeathMinigames = true
  minigameReducesRespawnTimer = false
  respawnMode = GROUP_RESPAWN
  respawnCooldown = 2
  respawnCooldownUnit = MINUTES

Usable personal spawnpoints take effect automatically. The
enableRespawningOnSpawnpoint entry is reserved for a death-screen button.

Set showTeammateDimensions=true on the server to display teammate
dimensions before spawning. This option supports both true and false;
the server controls the display for every client. When false, the server
also leaves teammate dimensions out of the status packets.

Test dummies and minigames support both true and false. With
enableDeathMinigames=true, click Minigames beside Open to LAN in the Game Menu
or on the death screen while waiting. The world keeps ticking while the
minigames screen is open, including in single-player. On the death screen,
the timer and teammate list stay visible even at Minecraft's minimum GUI
size. Choose Flappy Cart or Tetris using the tabs, and use Restart (R) to
start again. Flappy Cart starts on the first flap. Press Space, W, or Up,
or click the playfield to flap. Each obstacle passed earns one point. In
Tetris use A/D or Left/Right to move, W, Space, or Up to rotate, S or Down
to drop faster, and Enter to drop immediately. Each completed line earns
10 points. Click a game to focus its keyboard controls. Page Up/Page Down
always scrolls the teammate list.

Flappy Cart plays Cat and Tetris plays Blocks through the Music slider.
The soundtrack stops when the game ends or you leave the panel. Minecraft
sound effects mark game selection, flaps, block moves, and scores.
Losing plays a short descending note. Opening Minigames loads your best run
and the world's top player and score for each game. Both screens use the
same records, saved in the world data for online and offline players.

Set minigameReducesRespawnTimer=true to remove 10 seconds (200 server ticks)
per point earned on the death screen. The server simulates the games and
awards points.
Restarting or switching games does not award old points again. Reductions
only affect your own remaining timer and never bypass selecting a living teammate.

Changing any unsupported setting stops loading with:
  error - not implemented yet: <setting>

Spectating, stratagems/pods, custom groups, alternate respawn modes,
spawnpoint buttons, alternate timer settings, and hardcore worlds are
not implemented. There are no controls for these features in the UI.
Configuration changes take effect after restarting Minecraft/the server.

Development
-----------
Use a Java 8 JDK. The wrapper pins Gradle 5.6.4 and the build pins
ForgeGradle 3.0.197 plus Forge 14.23.5.2860.

  gradlew.bat build
  gradlew.bat test
  gradlew.bat genIntellijRuns

Open the project in IntelliJ and use the generated Minecraft Client run
configuration. For Eclipse, generate the run configuration with
gradlew.bat genEclipseRuns instead.

The generateResources DataGen task creates mcmod.info, pack.mcmeta, and
English localization from build.gradle. Edit those generation sources
and regenerate; do not manually edit generated resources. No textures,
item models, or recipes need to be shipped with the mod. The games use
Minecraft's minecart and ore sprites, and teammate icons use player skins.

Before using a world, verify on a separate RLCraft 2.9.3 test world with
at least two clients: one-player death, complete group wipe, simultaneous
deaths, target logout/death, reconnect during cooldown, server restart,
valid/broken beds or waystones, and cross-dimension regrouping. Also check
that normal item drops and RLCraft's health/death systems behave normally.
