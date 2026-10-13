package wile.engineersdecor;

import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;
import wile.engineersdecor.blocks.EdLadderBlock;
import wile.engineersdecor.libmc.Auxiliaries;
import wile.engineersdecor.libmc.OptionalRecipeCondition;
import wile.engineersdecor.libmc.Overlay;
import wile.engineersdecor.libmc.Registries;


@Mod("engineersdecor")
public class ModEngineersDecor
{
  public static final String MODID = "engineersdecor";
  public static final String MODNAME = "Engineer's Decor";
  public static final int VERSION_DATAFIXER = 0;
  private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

  public ModEngineersDecor(IEventBus modEventBus, ModContainer modContainer)
  {
    Auxiliaries.init(MODID, LOGGER, ModConfig::getServerConfig);
    Auxiliaries.logGitVersion(MODNAME);
    Registries.init(MODID, "sign_decor", (reg)->reg.register(modEventBus));
    ModContent.init(MODID, modEventBus);
    OptionalRecipeCondition.init(MODID, LOGGER);
    modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, ModConfig.SERVER_CONFIG_SPEC);
    modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON, ModConfig.COMMON_CONFIG_SPEC);
    modEventBus.addListener(this::onRegisterPayloads);
    modEventBus.addListener(this::onSetup);
    modEventBus.addListener(this::onClientSetup);
    modEventBus.addListener(ModContent::registerCapabilities);
    modEventBus.addListener(ModContent::registerMenuScreens);
    NeoForge.EVENT_BUS.register(this);
  }

  private void onRegisterPayloads(final RegisterPayloadHandlersEvent event)
  {
    wile.engineersdecor.libmc.Networking.register(event);
  }

  private void onSetup(final FMLCommonSetupEvent event)
  {
  }

  private void onClientSetup(final FMLClientSetupEvent event)
  {
    Overlay.TextOverlayGui.on_config(0.75, 0x00ffaa00, 0x55333333, 0x55333333, 0x55444444);
    wile.engineersdecor.libmc.Networking.OverlayTextMessage.setHandler(Overlay.TextOverlayGui::show);
    ModContent.registerBlockEntityRenderers(event);
    ModContent.processContentClientSide(event);
  }

  @net.neoforged.fml.common.EventBusSubscriber(modid=MODID)
  public static class ForgeEvents
  {
    @SubscribeEvent
    public static void onConfigLoad(final ModConfigEvent.Loading event)
    { ModConfig.apply(); }

    @SubscribeEvent
    public static void onConfigReload(final ModConfigEvent.Reloading event)
    {
      try {
        Auxiliaries.logger().info("Config file changed {}", event.getConfig().getFileName());
        ModConfig.apply();
      } catch(Throwable e) {
        Auxiliaries.logger().error("Failed to load changed config: " + e.getMessage());
      }
    }
  }

  @SubscribeEvent
  public void onPlayerEvent(final net.neoforged.neoforge.event.tick.EntityTickEvent.Post event)
  {
    if(!(event.getEntity() instanceof final Player player)) return;
    if(player.onClimbable()) EdLadderBlock.onPlayerUpdateEvent(player);
  }

  @OnlyIn(Dist.CLIENT)
  @net.neoforged.fml.common.EventBusSubscriber(value=Dist.CLIENT, modid=MODID)
  public static class ForgeClientEvents
  {
    @SubscribeEvent
    public static void onRenderGui(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Post event)
    {
      if(event.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.CAMERA_OVERLAYS)) {
        Overlay.TextOverlayGui.INSTANCE.onRenderGui(event.getGuiGraphics());
      }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public static void onRenderWorldOverlay(net.neoforged.neoforge.client.event.RenderLevelStageEvent event)
    {
      if(event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_WEATHER) {
        Overlay.TextOverlayGui.INSTANCE.onRenderWorldOverlay(event.getPoseStack(), event.getPartialTick().getGameTimeDeltaPartialTick(true));
      }
    }
  }

}

