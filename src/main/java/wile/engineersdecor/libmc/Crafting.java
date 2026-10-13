/*
 * @file Recipes.java
 * @author Stefan Wilhelm (wile)
 * @copyright (C) 2020 Stefan Wilhelm
 * @license MIT (see https://opensource.org/licenses/MIT)
 *
 * Recipe utility functionality.
 */
package wile.engineersdecor.libmc;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ComposterBlock;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.BiPredicate;


public class Crafting
{
  // -------------------------------------------------------------------------------------------------------------------

  public static final class CraftingGrid
  {
    protected static final CraftingGrid instance3x3 = new CraftingGrid(3, 3);
    private final int width;
    private final int height;

    protected CraftingGrid(int width, int height)
    {
      this.width = width;
      this.height = height;
    }

    private CraftingInput toInput(Container grid)
    {
      List<ItemStack> items = new ArrayList<>(width * height);
      for(int i = 0; i < width * height; ++i) {
        items.add(i < grid.getContainerSize() ? grid.getItem(i) : ItemStack.EMPTY);
      }
      return CraftingInput.of(width, height, items);
    }

    public List<CraftingRecipe> getRecipes(Level world, Container grid)
    {
      CraftingInput input = toInput(grid);
      return world.getRecipeManager().getRecipesFor(RecipeType.CRAFTING, input, world)
        .stream().map(RecipeHolder::value).toList();
    }

    public List<ItemStack> getRemainingItems(Level world, Container grid, CraftingRecipe recipe)
    {
      CraftingInput input = toInput(grid);
      return recipe.getRemainingItems(input);
    }

    public ItemStack getCraftingResult(Level world, Container grid, CraftingRecipe recipe)
    {
      CraftingInput input = toInput(grid);
      return recipe.assemble(input, world.registryAccess());
    }
  }

  /**
   * Returns a Crafting recipe by registry name.
   */
  public static Optional<CraftingRecipe> getCraftingRecipe(Level world, ResourceLocation recipe_id)
  {
    return world.getRecipeManager().byKey(recipe_id)
      .map(RecipeHolder::value)
      .filter(r -> r instanceof CraftingRecipe)
      .map(r -> (CraftingRecipe) r);
  }

  /**
   * Returns a list of matching recipes by the first N slots (crafting grid slots) of the given inventory.
   */
  public static List<CraftingRecipe> get3x3CraftingRecipes(Level world, Container crafting_grid_slots)
  { return CraftingGrid.instance3x3.getRecipes(world, crafting_grid_slots); }

  /**
   * Returns a recipe by the first N slots (crafting grid slots).
   */
  public static Optional<CraftingRecipe> get3x3CraftingRecipe(Level world, Container crafting_grid_slots)
  { return get3x3CraftingRecipes(world, crafting_grid_slots).stream().findFirst(); }

  /**
   * Returns the result item of the recipe with the given grid layout.
   */
  public static ItemStack get3x3CraftingResult(Level world, Container grid, CraftingRecipe recipe)
  { return CraftingGrid.instance3x3.getCraftingResult(world, grid, recipe); }

  /**
   * Returns the items remaining in the grid after crafting 3x3.
   */
  public static List<ItemStack> get3x3RemainingItems(Level world, Container grid, CraftingRecipe recipe)
  { return CraftingGrid.instance3x3.getRemainingItems(world, grid, recipe); }

  public static List<ItemStack> get3x3Placement(Level world, CraftingRecipe recipe, Container item_inventory, @Nullable Container crafting_grid)
  {
    final int width = 3;
    final int height = 3;
    if(!recipe.canCraftInDimensions(width,height)) return Collections.emptyList();
    List<ItemStack> used = new ArrayList<>();   //NonNullList.withSize(width*height);
    for(int i=width*height; i>0; --i) used.add(ItemStack.EMPTY);
    Container check_inventory = Inventories.copyOf(item_inventory);
    Inventories.InventoryRange source = new Inventories.InventoryRange(check_inventory);
    final List<Ingredient> ingredients = recipe.getIngredients();
    final List<ItemStack> preferred = new ArrayList<>(width*height);
    if(crafting_grid != null) {
      for(int i=0; i<crafting_grid.getContainerSize(); ++i) {
        ItemStack stack = crafting_grid.getItem(i);
        if(stack.isEmpty()) continue;
        stack = stack.copy();
        stack.setCount(1);
        if(!source.extract(stack).isEmpty()) preferred.add(stack);
      }
    }
    for(int i=0; i<ingredients.size(); ++i) {
      final Ingredient ingredient = ingredients.get(i);
      if(ingredient == Ingredient.EMPTY) continue;
      ItemStack stack = preferred.stream().filter(ingredient).findFirst().orElse(ItemStack.EMPTY);
      if(!stack.isEmpty()) {
        preferred.remove(stack);
      } else {
        stack = source.stream().filter(ingredient).findFirst().orElse(ItemStack.EMPTY);
        if(stack.isEmpty()) return Collections.emptyList();
        stack = stack.copy();
        stack.setCount(1);
        if(source.extract(stack).isEmpty()) return Collections.emptyList();
      }
      used.set(i, stack);
    }
    if(recipe instanceof ShapedRecipe shaped) {
      List<ItemStack> placement = NonNullList.withSize(width*height, ItemStack.EMPTY);
      for(int row=0; row<shaped.getHeight(); ++row) {
        for(int col=0; col<shaped.getWidth(); ++col) {
          placement.set(width*row+col, used.get(row*shaped.getWidth()+col));
        }
      }
      return placement;
    } else {
      return used;
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Returns the recipe for a given input stack to smelt, null if there is no recipe
   * for the given type (SMELTING,BLASTING,SMOKING, etc).
   */
  public static <T extends AbstractCookingRecipe> Optional<T> getFurnaceRecipe(RecipeType<T> recipe_type, Level world, ItemStack input_stack)
  {
    if(input_stack.isEmpty()) return Optional.empty();
    SingleRecipeInput input = new SingleRecipeInput(input_stack);
    return world.getRecipeManager().getRecipeFor(recipe_type, input, world).map(RecipeHolder::value);
  }

  public static <T extends AbstractCookingRecipe> int getSmeltingTimeNeeded(RecipeType<T> recipe_type, Level world, ItemStack stack)
  {
    if(stack.isEmpty()) return 0;
    final int t = getFurnaceRecipe(recipe_type, world, stack).map((AbstractCookingRecipe::getCookingTime)).orElse(0);
    return (t<=0) ? 200 : t;
  }

  /**
   * Returns the burn time of an item when used as fuel, 0 if it is no fuel.
   */
  public static int getFuelBurntime(Level world, ItemStack stack)
  {
    if(stack.isEmpty()) return 0;
    int t = stack.getBurnTime(RecipeType.SMELTING);
    return Math.max(t, 0);
  }

  /**
   * Returns true if an item can be used as fuel.
   */
  public static boolean isFuel(Level world, ItemStack stack)
  { return (getFuelBurntime(world, stack) > 0) || (stack.getItem()==Items.LAVA_BUCKET); }

  /**
   * Returns burntime and remaining stack then the item shall be used as fuel.
   */
  public static Tuple<Integer,ItemStack> consumeFuel(Level world, ItemStack stack)
  {
    if(stack.isEmpty()) return new Tuple<>(0,stack);
    int burnime = getFuelBurntime(world, stack);
    if((stack.getItem()==Items.LAVA_BUCKET)) {
      if(burnime <= 0) burnime = 1000*20;
      return new Tuple<>(burnime,new ItemStack(Items.BUCKET));
    } else if(burnime <= 0) {
      return new Tuple<>(0,stack);
    } else {
      ItemStack left_over = stack.copy();
      left_over.shrink(1);
      return new Tuple<>(burnime,left_over);
    }
  }

  // -------------------------------------------------------------------------------------------------------------------

  /**
   * Returns true if the item can be used as brewing fuel.
   */
  public static boolean isBrewingFuel(Level world, ItemStack stack)
  { return (stack.getItem() == Items.BLAZE_POWDER) || (stack.getItem() == Items.BLAZE_ROD); }

  /**
   * Returns true if the item can be used as brewing ingredient.
   */
  public static boolean isBrewingIngredient(Level world, ItemStack stack)
  { return (world != null) && world.potionBrewing().isIngredient(stack); }

  /**
   * Returns true if the item can be used as brewing bottle.
   */
  public static boolean isBrewingInput(Level world, ItemStack stack)
  { return (world != null) && (stack.getItem() == Items.POTION || stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION || stack.getItem() == Items.GLASS_BOTTLE); }

  /**
   * Returns the burn time for brewing of the given stack.
   */
  public static int getBrewingFuelBurntime(Level world, ItemStack stack)
  {
    if(stack.isEmpty()) return 0;
    if(stack.getItem() == Items.BLAZE_POWDER) return (400*20);
    if(stack.getItem() == Items.BLAZE_ROD) return (400*40);
    return 0;
  }

  /**
   * Returns brewing burn time and remaining stack if the item shall be used as fuel.
   */
  public static Tuple<Integer,ItemStack> consumeBrewingFuel(Level world, ItemStack stack)
  {
    int burntime = getBrewingFuelBurntime(world, stack);
    if(burntime <= 0) return new Tuple<>(0, stack.copy());
    stack = stack.copy();
    stack.shrink(1);
    return new Tuple<>(burntime, stack.isEmpty() ? ItemStack.EMPTY : stack);
  }

  // -------------------------------------------------------------------------------------------------------------------

  public static double getCompostingChance(ItemStack stack)
  { return ComposterBlock.COMPOSTABLES.getOrDefault(stack.getItem(), 0.0f); }

}