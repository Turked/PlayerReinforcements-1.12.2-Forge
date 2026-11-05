package com.playerreinforcements;

import com.playerreinforcements.config.ReinforcementsConfig;
import com.playerreinforcements.network.ReinforcementNetwork;
import com.playerreinforcements.proxy.CommonProxy;
import com.playerreinforcements.server.ReinforcementManager;
import com.playerreinforcements.server.RespawnPacketGuard;
import com.playerreinforcements.server.TestDummyManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import org.apache.logging.log4j.Logger;

@Mod(modid = PlayerReinforcements.MODID, name = "Player Reinforcements", version = PlayerReinforcements.VERSION,
        acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:forge@[14.23.5.2860,)")
public final class PlayerReinforcements {
    public static final String MODID = "playerreinforcements";
    public static final String VERSION = "0.2.5";
    public static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);

    @SidedProxy(clientSide = "com.playerreinforcements.proxy.ClientProxy",
            serverSide = "com.playerreinforcements.proxy.CommonProxy")
    public static CommonProxy PROXY;
    public static Logger LOGGER;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER = event.getModLog();
        ReinforcementsConfig.load(event.getSuggestedConfigurationFile());
        ReinforcementNetwork.register();
        MinecraftForge.EVENT_BUS.register(ReinforcementManager.INSTANCE);
        MinecraftForge.EVENT_BUS.register(new RespawnPacketGuard());
        MinecraftForge.EVENT_BUS.register(TestDummyManager.INSTANCE);
        PROXY.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (event.getServer().isHardcore()) {
            throw new IllegalStateException("error - not implemented yet: hardcore worlds");
        }
        ReinforcementManager.INSTANCE.start(event.getServer());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ReinforcementManager.INSTANCE.stop();
    }
}
