package wile.engineersdecor.libmc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class Networking
{
  public static void register(final RegisterPayloadHandlersEvent event)
  {
    final PayloadRegistrar registrar = event.registrar("1");

    registrar.playToServer(
      PacketTileNotifyClientToServer.TYPE,
      PacketTileNotifyClientToServer.STREAM_CODEC,
      PacketTileNotifyClientToServer::handle
    );

    registrar.playToClient(
      PacketTileNotifyServerToClient.TYPE,
      PacketTileNotifyServerToClient.STREAM_CODEC,
      PacketTileNotifyServerToClient::handle
    );

    registrar.playToServer(
      PacketContainerSyncClientToServer.TYPE,
      PacketContainerSyncClientToServer.STREAM_CODEC,
      PacketContainerSyncClientToServer::handle
    );

    registrar.playToClient(
      PacketContainerSyncServerToClient.TYPE,
      PacketContainerSyncServerToClient.STREAM_CODEC,
      PacketContainerSyncServerToClient::handle
    );

    registrar.playToServer(
      PacketNbtNotifyClientToServer.TYPE,
      PacketNbtNotifyClientToServer.STREAM_CODEC,
      PacketNbtNotifyClientToServer::handle
    );

    registrar.playToClient(
      PacketNbtNotifyServerToClient.TYPE,
      PacketNbtNotifyServerToClient.STREAM_CODEC,
      PacketNbtNotifyServerToClient::handle
    );

    registrar.playToClient(
      OverlayTextMessage.TYPE,
      OverlayTextMessage.STREAM_CODEC,
      OverlayTextMessage::handle
    );
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Tile entity notifications
  //--------------------------------------------------------------------------------------------------------------------

  public interface IPacketTileNotifyReceiver
  {
    default void onServerPacketReceived(CompoundTag nbt) {}
    default void onClientPacketReceived(Player player, CompoundTag nbt) {}
  }

  public record PacketTileNotifyClientToServer(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketTileNotifyClientToServer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "tile_notify_c2s"));

    public static final StreamCodec<FriendlyByteBuf, PacketTileNotifyClientToServer> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, PacketTileNotifyClientToServer::pos,
      ByteBufCodecs.COMPOUND_TAG, PacketTileNotifyClientToServer::nbt,
      PacketTileNotifyClientToServer::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void sendToServer(BlockPos pos, CompoundTag nbt)
    { if((pos!=null) && (nbt!=null)) PacketDistributor.sendToServer(new PacketTileNotifyClientToServer(pos, nbt)); }

    public static void sendToServer(BlockEntity te, CompoundTag nbt)
    { if((te!=null) && (nbt!=null)) sendToServer(te.getBlockPos(), nbt); }

    public static void handle(final PacketTileNotifyClientToServer pkt, final IPayloadContext ctx)
    {
      ctx.enqueueWork(() -> {
        Player player = ctx.player();
        if(player==null) return;
        Level world = player.level();
        final BlockEntity te = world.getBlockEntity(pkt.pos);
        if(!(te instanceof IPacketTileNotifyReceiver)) return;
        ((IPacketTileNotifyReceiver)te).onClientPacketReceived(player, pkt.nbt);
      });
    }
  }

  public record PacketTileNotifyServerToClient(BlockPos pos, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketTileNotifyServerToClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "tile_notify_s2c"));

    public static final StreamCodec<FriendlyByteBuf, PacketTileNotifyServerToClient> STREAM_CODEC = StreamCodec.composite(
      BlockPos.STREAM_CODEC, PacketTileNotifyServerToClient::pos,
      ByteBufCodecs.COMPOUND_TAG, PacketTileNotifyServerToClient::nbt,
      PacketTileNotifyServerToClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void sendToPlayer(Player player, BlockEntity te, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer sp)) || (player instanceof FakePlayer) || (te==null) || (nbt==null)) return;
      PacketDistributor.sendToPlayer(sp, new PacketTileNotifyServerToClient(te.getBlockPos(), nbt));
    }

    public static void sendToPlayers(BlockEntity te, CompoundTag nbt)
    {
      if(te==null || te.getLevel()==null) return;
      for(Player player: te.getLevel().players()) sendToPlayer(player, te, nbt);
    }

    public static void handle(final PacketTileNotifyServerToClient pkt, final IPayloadContext ctx)
    {
      ctx.enqueueWork(() -> {
        Level world = SidedProxy.getWorldClientSide();
        if(world == null) return;
        final BlockEntity te = world.getBlockEntity(pkt.pos);
        if(!(te instanceof IPacketTileNotifyReceiver)) return;
        ((IPacketTileNotifyReceiver)te).onServerPacketReceived(pkt.nbt);
      });
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Container synchronization
  //--------------------------------------------------------------------------------------------------------------------

  public interface INetworkSynchronisableContainer
  {
    void onServerPacketReceived(int windowId, CompoundTag nbt);
    void onClientPacketReceived(int windowId, Player player, CompoundTag nbt);
  }

  public record PacketContainerSyncClientToServer(int id, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketContainerSyncClientToServer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "container_sync_c2s"));

    public static final StreamCodec<FriendlyByteBuf, PacketContainerSyncClientToServer> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, PacketContainerSyncClientToServer::id,
      ByteBufCodecs.COMPOUND_TAG, PacketContainerSyncClientToServer::nbt,
      PacketContainerSyncClientToServer::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void sendToServer(int windowId, CompoundTag nbt)
    { if(nbt!=null) PacketDistributor.sendToServer(new PacketContainerSyncClientToServer(windowId, nbt)); }

    public static void sendToServer(AbstractContainerMenu container, CompoundTag nbt)
    { if(nbt!=null) sendToServer(container.containerId, nbt); }

    public static void handle(final PacketContainerSyncClientToServer pkt, final IPayloadContext ctx)
    {
      ctx.enqueueWork(() -> {
        Player player = ctx.player();
        if((player==null) || !(player.containerMenu instanceof INetworkSynchronisableContainer)) return;
        if(player.containerMenu.containerId != pkt.id) return;
        ((INetworkSynchronisableContainer)player.containerMenu).onClientPacketReceived(pkt.id, player, pkt.nbt);
      });
    }
  }

  public record PacketContainerSyncServerToClient(int id, CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketContainerSyncServerToClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "container_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, PacketContainerSyncServerToClient> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.VAR_INT, PacketContainerSyncServerToClient::id,
      ByteBufCodecs.COMPOUND_TAG, PacketContainerSyncServerToClient::nbt,
      PacketContainerSyncServerToClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void sendToPlayer(Player player, int windowId, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer sp)) || (player instanceof FakePlayer) || (nbt==null)) return;
      PacketDistributor.sendToPlayer(sp, new PacketContainerSyncServerToClient(windowId, nbt));
    }

    public static void sendToPlayer(Player player, AbstractContainerMenu container, CompoundTag nbt)
    { sendToPlayer(player, container.containerId, nbt); }

    public static <C extends AbstractContainerMenu & INetworkSynchronisableContainer>
    void sendToListeners(Level world, C container, CompoundTag nbt)
    {
      for(Player player: world.players()) {
        if(player.containerMenu.containerId != container.containerId) continue;
        sendToPlayer(player, container.containerId, nbt);
      }
    }

    public static void handle(final PacketContainerSyncServerToClient pkt, final IPayloadContext ctx)
    {
      ctx.enqueueWork(() -> {
        Player player = SidedProxy.getPlayerClientSide();
        if((player==null) || !(player.containerMenu instanceof INetworkSynchronisableContainer)) return;
        if(player.containerMenu.containerId != pkt.id) return;
        ((INetworkSynchronisableContainer)player.containerMenu).onServerPacketReceived(pkt.id, pkt.nbt);
      });
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // World notifications
  //--------------------------------------------------------------------------------------------------------------------

  public record PacketNbtNotifyClientToServer(CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketNbtNotifyClientToServer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "nbt_notify_c2s"));
    public static final Map<String, BiConsumer<Player, CompoundTag>> handlers = new HashMap<>();

    public static final StreamCodec<FriendlyByteBuf, PacketNbtNotifyClientToServer> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.COMPOUND_TAG, PacketNbtNotifyClientToServer::nbt,
      PacketNbtNotifyClientToServer::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void sendToServer(CompoundTag nbt)
    { if(nbt!=null) PacketDistributor.sendToServer(new PacketNbtNotifyClientToServer(nbt)); }

    public static void handle(final PacketNbtNotifyClientToServer pkt, final IPayloadContext ctx)
    {
      ctx.enqueueWork(() -> {
        final Player player = ctx.player();
        if(player==null) return;
        final String hnd = pkt.nbt.getString("hnd");
        if(hnd.isEmpty()) return;
        if(handlers.containsKey(hnd)) handlers.get(hnd).accept(player, pkt.nbt);
      });
    }
  }

  public record PacketNbtNotifyServerToClient(CompoundTag nbt) implements CustomPacketPayload
  {
    public static final Type<PacketNbtNotifyServerToClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "nbt_notify_s2c"));
    public static final Map<String, Consumer<CompoundTag>> handlers = new HashMap<>();

    public static final StreamCodec<FriendlyByteBuf, PacketNbtNotifyServerToClient> STREAM_CODEC = StreamCodec.composite(
      ByteBufCodecs.COMPOUND_TAG, PacketNbtNotifyServerToClient::nbt,
      PacketNbtNotifyServerToClient::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void sendToPlayer(Player player, CompoundTag nbt)
    {
      if((!(player instanceof ServerPlayer sp)) || (player instanceof FakePlayer) || (nbt==null)) return;
      PacketDistributor.sendToPlayer(sp, new PacketNbtNotifyServerToClient(nbt));
    }

    public static void sendToPlayers(Level world, CompoundTag nbt)
    { for(Player player: world.players()) sendToPlayer(player, nbt); }

    public static void handle(final PacketNbtNotifyServerToClient pkt, final IPayloadContext ctx)
    {
      ctx.enqueueWork(() -> {
        final String hnd = pkt.nbt.getString("hnd");
        if(hnd.isEmpty()) return;
        if(handlers.containsKey(hnd)) handlers.get(hnd).accept(pkt.nbt);
      });
    }
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Main window GUI text message
  //--------------------------------------------------------------------------------------------------------------------

  public record OverlayTextMessage(Component text, int delay) implements CustomPacketPayload
  {
    public static final Type<OverlayTextMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Auxiliaries.modid(), "overlay_text"));
    public static final int DISPLAY_TIME_MS = 3000;
    private static BiConsumer<Component, Integer> handler_ = null;

    public static final StreamCodec<RegistryFriendlyByteBuf, OverlayTextMessage> STREAM_CODEC = StreamCodec.composite(
      ComponentSerialization.STREAM_CODEC, OverlayTextMessage::text,
      ByteBufCodecs.VAR_INT, OverlayTextMessage::delay,
      OverlayTextMessage::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type()
    { return TYPE; }

    public static void setHandler(BiConsumer<Component, Integer> handler)
    { if(handler_==null) handler_ = handler; }

    public static void sendToPlayer(Player player, Component message, int delay)
    {
      if((!(player instanceof ServerPlayer sp)) || (player instanceof FakePlayer) || Auxiliaries.isEmpty(message)) return;
      PacketDistributor.sendToPlayer(sp, new OverlayTextMessage(message.copy(), delay));
    }

    public static void handle(final OverlayTextMessage pkt, final IPayloadContext ctx)
    {
      if(handler_ != null) ctx.enqueueWork(() -> handler_.accept(pkt.text(), pkt.delay()));
    }
  }
}
