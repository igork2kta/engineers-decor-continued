package wile.engineersdecor.libmc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public class OptionalRecipeCondition implements ICondition
{
  public static final MapCodec<OptionalRecipeCondition> CODEC = RecordCodecBuilder.mapCodec(
    builder -> builder.group(
      ResourceLocation.CODEC.optionalFieldOf("result").forGetter(c -> Optional.ofNullable(c.result)),
      ResourceLocation.CODEC.listOf().optionalFieldOf("required", List.of()).forGetter(c -> c.all_required),
      ResourceLocation.CODEC.listOf().optionalFieldOf("missing", List.of()).forGetter(c -> c.any_missing),
      ResourceLocation.CODEC.listOf().optionalFieldOf("required_tags", List.of()).forGetter(c -> c.all_required_tags),
      ResourceLocation.CODEC.listOf().optionalFieldOf("missing_tags", List.of()).forGetter(c -> c.any_missing_tags),
      Codec.BOOL.optionalFieldOf("experimental", false).forGetter(c -> c.experimental),
      Codec.BOOL.optionalFieldOf("result_is_tag", false).forGetter(c -> c.result_is_tag)
    ).apply(builder, (res, req, mis, req_t, mis_t, exp, res_t) ->
      new OptionalRecipeCondition(res.orElse(null), req, mis, req_t, mis_t, exp, res_t)
    )
  );

  private final List<ResourceLocation> all_required;
  private final List<ResourceLocation> any_missing;
  private final List<ResourceLocation> all_required_tags;
  private final List<ResourceLocation> any_missing_tags;
  private final ResourceLocation result;
  private final boolean result_is_tag;
  private final boolean experimental;

  private static boolean with_experimental = false;
  private static boolean without_recipes = false;
  private static Predicate<Block> block_optouts = (block)->false;
  private static Predicate<Item> item_optouts = (item)->false;

  public static void init(String modid, Logger logger)
  {}

  public static void on_config(boolean enable_experimental, boolean disable_all_recipes,
                               Predicate<Block> block_optout_provider,
                               Predicate<Item> item_optout_provider)
  {
    with_experimental = enable_experimental;
    without_recipes = disable_all_recipes;
    block_optouts = block_optout_provider;
    item_optouts = item_optout_provider;
  }

  public OptionalRecipeCondition(ResourceLocation result, List<ResourceLocation> required, List<ResourceLocation> missing, List<ResourceLocation> required_tags, List<ResourceLocation> missing_tags, boolean isexperimental, boolean result_is_tag)
  {
    this.all_required = required;
    this.any_missing = missing;
    this.all_required_tags = required_tags;
    this.any_missing_tags = missing_tags;
    this.result = result;
    this.result_is_tag = result_is_tag;
    this.experimental = isexperimental;
  }

  @Override
  public MapCodec<? extends ICondition> codec()
  {
    return CODEC;
  }

  @Override
  public boolean test(IContext context)
  {
    if(without_recipes) return false;
    if((experimental) && (!with_experimental)) return false;

    if(result != null) {
      if(!BuiltInRegistries.ITEM.containsKey(result)) return false;
      Item item = BuiltInRegistries.ITEM.get(result);
      if(item_optouts.test(item)) return false;
      if(BuiltInRegistries.BLOCK.containsKey(result) && block_optouts.test(BuiltInRegistries.BLOCK.get(result))) return false;
    }
    if(!all_required.isEmpty()) {
      for(ResourceLocation rl : all_required) {
        if(!BuiltInRegistries.ITEM.containsKey(rl)) return false;
      }
    }
    if(!all_required_tags.isEmpty()) {
      for(ResourceLocation rl : all_required_tags) {
        TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, rl);
        if(!BuiltInRegistries.ITEM.getTag(tag).isPresent()) return false;
      }
    }
    if(!any_missing.isEmpty()) {
      for(ResourceLocation rl : any_missing) {
        if(!BuiltInRegistries.ITEM.containsKey(rl)) return true;
      }
      return false;
    }
    if(!any_missing_tags.isEmpty()) {
      for(ResourceLocation rl : any_missing_tags) {
        TagKey<Item> tag = TagKey.create(net.minecraft.core.registries.Registries.ITEM, rl);
        if(!BuiltInRegistries.ITEM.getTag(tag).isPresent()) return true;
      }
      return false;
    }
    return true;
  }
}
