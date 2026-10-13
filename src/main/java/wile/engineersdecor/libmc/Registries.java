/*
 * @file Registries.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Common game registry handling.
 */
package wile.engineersdecor.libmc;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import wile.engineersdecor.ModEngineersDecor;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Registries
{
  private static String modid = null;
  private static String creative_tab_icon = "";

  public static final DeferredRegister<CreativeModeTab> creative_tabs =
          DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, ModEngineersDecor.MODID);
  private static final Map<String, TagKey<Block>> registered_block_tag_keys = new HashMap<>();
  private static final Map<String, TagKey<Item>> registered_item_tag_keys = new HashMap<>();

  private static final Map<String, DeferredHolder<Block, ? extends Block>> registered_blocks = new HashMap<>();
  private static final Map<String, DeferredHolder<Item, ? extends Item>> registered_items = new HashMap<>();
  private static final Map<String, DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<?>>> registered_block_entity_types = new HashMap<>();
  private static final Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<?>>> registered_entity_types = new HashMap<>();
  private static final Map<String, DeferredHolder<MenuType<?>, ? extends MenuType<?>>> registered_menu_types = new HashMap<>();
  private static final Map<String, DeferredHolder<RecipeSerializer<?>, ? extends RecipeSerializer<?>>> recipe_serializers = new HashMap<>();

  private static DeferredRegister<Block> BLOCKS;
  private static DeferredRegister<Item> ITEMS;
  private static DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES;
  private static DeferredRegister<MenuType<?>> MENUS;
  private static DeferredRegister<EntityType<?>> ENTITIES;
  private static DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS;
  private static List<DeferredRegister<?>> MOD_REGISTRIES;
  private static DeferredHolder<CreativeModeTab, CreativeModeTab> creative_tab;

  public static void init(String mod_id, String creative_tab_icon_item_name, Consumer<DeferredRegister<?>> registrar)
  {
    modid = mod_id;
    creative_tab_icon = creative_tab_icon_item_name;
    BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, modid);
    ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, modid);
    BLOCK_ENTITIES = DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, modid);
    MENUS = DeferredRegister.create(BuiltInRegistries.MENU, modid);
    ENTITIES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, modid);
    RECIPE_SERIALIZERS = DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, modid);
    List.of(BLOCKS, ITEMS, BLOCK_ENTITIES, MENUS, ENTITIES, RECIPE_SERIALIZERS).forEach(registrar);
  }

public static void addCreativeTab(){
    creative_tab = creative_tabs.register("tab" + modid,
          () -> CreativeModeTab.builder().icon( () -> new ItemStack(getItem(creative_tab_icon)))
                  .title(Component.translatable("itemGroup.tabengineersdecor"))
                  .displayItems((itemDisplayParameters, output) -> {
                    getRegisteredItems().forEach(item -> output.accept(item.asItem()));
                  })
                  .build());


}

  public static void register(IEventBus eventBus){
    creative_tabs.register(eventBus);
  }

  // -------------------------------------------------------------------------------------------------------------

  public static Block getBlock(String block_name)
  { return registered_blocks.get(block_name).get(); }

  public static Item getItem(String name)
  { return registered_items.get(name).get(); }

  public static EntityType<?> getEntityType(String name)
  { return registered_entity_types.get(name).get(); }

  public static BlockEntityType<?> getBlockEntityType(String name)
  {
    DeferredHolder<BlockEntityType<?>, ? extends BlockEntityType<?>> holder = registered_block_entity_types.get(name);
    if(holder == null) throw new IllegalArgumentException("No BlockEntityType registered with key: " + name);
    return holder.get();
  }

  public static MenuType<?> getMenuType(String name)
  { return registered_menu_types.get(name).get(); }

  public static RecipeSerializer<?> getRecipeSerializer(String name)
  { return recipe_serializers.get(name).get(); }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(String block_name)
  {
    if(registered_block_entity_types.containsKey("tet_" + block_name)) {
      return getBlockEntityType("tet_" + block_name);
    } else if(registered_block_entity_types.containsKey(block_name)) {
      return getBlockEntityType(block_name);
    }
    throw new IllegalArgumentException("No BlockEntityType found for block: " + block_name);
  }

  public static BlockEntityType<?> getBlockEntityTypeOfBlock(Block block)
  { return getBlockEntityTypeOfBlock(BuiltInRegistries.BLOCK.getKey(block).getPath()); }

  public static MenuType<?> getMenuTypeOfBlock(String name)
  { return getMenuType("ct_"+name); }

  public static MenuType<?> getMenuTypeOfBlock(Block block)
  { return getMenuTypeOfBlock(BuiltInRegistries.BLOCK.getKey(block).getPath()); }

  public static TagKey<Block> getBlockTagKey(String name)
  { return registered_block_tag_keys.get(name); }

  public static TagKey<Item> getItemTagKey(String name)
  { return registered_item_tag_keys.get(name); }

  // -------------------------------------------------------------------------------------------------------------

  @Nonnull
  public static List<Block> getRegisteredBlocks()
  { return Collections.unmodifiableList(registered_blocks.values().stream().map(DeferredHolder::get).toList()); }

  @Nonnull
  public static List<Item> getRegisteredItems()
  { return Collections.unmodifiableList(registered_items.values().stream().map(DeferredHolder::get).toList()); }

  @Nonnull
  public static List<BlockEntityType<?>> getRegisteredBlockEntityTypes()
  { return Collections.unmodifiableList(registered_block_entity_types.values().stream().map(DeferredHolder::get).toList()); }

  @Nonnull
  public static List<EntityType<?>> getRegisteredEntityTypes()
  { return Collections.unmodifiableList(registered_entity_types.values().stream().map(DeferredHolder::get).toList()); }

  // -------------------------------------------------------------------------------------------------------------

  public static <T extends Item> void addItem(String registry_name, Supplier<T> supplier)
  { registered_items.put(registry_name, ITEMS.register(registry_name, supplier)); }

  public static <T extends Block> void addBlock(String registry_name, Supplier<T> block_supplier)
  {
      registered_blocks.put(registry_name, BLOCKS.register(registry_name, block_supplier));
      registered_items.put(registry_name, ITEMS.register(registry_name, () -> new BlockItem(registered_blocks.get(registry_name).get(), new Item.Properties())));
  }

  public static <T extends Block> void regist(String registry_name, Supplier<T> block_supplier)
  {
    DeferredHolder<Block, T> toReturn = BLOCKS.register(registry_name, block_supplier);
    ITEMS.register(registry_name, () -> new BlockItem(toReturn.get(), new Item.Properties()));
  }

  public static <TB extends Block, TI extends Item> void addBlock(String registry_name, Supplier<TB> block_supplier, Supplier<TI> item_supplier)
  {
    registered_blocks.put(registry_name, BLOCKS.register(registry_name, block_supplier));
    registered_items.put(registry_name, ITEMS.register(registry_name, item_supplier));
  }

  public static <T extends BlockEntity> void addBlockEntityType(String registry_name, BlockEntityType.BlockEntitySupplier<T> ctor, String... block_names)
  {
    registered_block_entity_types.put(registry_name, BLOCK_ENTITIES.register(registry_name, ()->{
      final Block[] blocks = Arrays.stream(block_names).map(s->{
        Block b = BLOCKS.getEntries().stream().filter((ro)->ro.getId().getPath().equals(s)).findFirst().map(DeferredHolder::get).orElse(null);
        if(b==null) Auxiliaries.logError("registered_blocks does not encompass '" + s + "'");
        return b;
      }).filter(Objects::nonNull).collect(Collectors.toList()).toArray(new Block[]{});
      return BlockEntityType.Builder.of(ctor, blocks).build(null);
    }));
  }

  public static <T extends EntityType<?>> void addEntityType(String registry_name, Supplier<EntityType<?>> supplier)
  { registered_entity_types.put(registry_name, ENTITIES.register(registry_name, supplier)); }

  public static <T extends MenuType<?>> void addMenuType(String registry_name, MenuType.MenuSupplier<?> supplier)
  { registered_menu_types.put(registry_name, MENUS.register(registry_name, ()->new MenuType<>(supplier,null))); }

  public static void addRecipeSerializer(String registry_name, Supplier<? extends RecipeSerializer<?>> serializer_supplier)
  { recipe_serializers.put(registry_name, RECIPE_SERIALIZERS.register(registry_name, serializer_supplier)); }

  public static void addOptionalBlockTag(String tag_name, ResourceLocation... default_blocks)
  {
    final TagKey<Block> key = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(modid, tag_name));
    registered_block_tag_keys.put(tag_name, key);
  }

  public static void addOptionaItemTag(String tag_name, ResourceLocation... default_items)
  {
    final TagKey<Item> key = TagKey.create(net.minecraft.core.registries.Registries.ITEM, ResourceLocation.fromNamespaceAndPath(modid, tag_name));
    registered_item_tag_keys.put(tag_name, key);
  }

  // -------------------------------------------------------------------------------------------------------------

  public static <TB extends Block, TI extends Item> void addBlock(String registry_name, Supplier<TB> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder)
  {
    addBlock(registry_name, block_supplier, ()->item_builder.apply(registered_blocks.get(registry_name).get(), (new Item.Properties())));
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor)
  {
    addBlock(registry_name, block_supplier);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor)
  {
    addBlock(registry_name, block_supplier, item_builder);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BiFunction<Block, Item.Properties, Item> item_builder, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier)
  {
    addBlock(registry_name, block_supplier, item_builder);
    addBlockEntityType("tet_"+registry_name, block_entity_ctor, registry_name);
    addMenuType("ct_"+registry_name, menu_type_supplier);
  }

  public static void addBlock(String registry_name, Supplier<? extends Block> block_supplier, BlockEntityType.BlockEntitySupplier<?> block_entity_ctor, MenuType.MenuSupplier<?> menu_type_supplier)
  {
    addBlock(registry_name, block_supplier, block_entity_ctor);
    addMenuType("ct_"+registry_name, menu_type_supplier);
  }

}
