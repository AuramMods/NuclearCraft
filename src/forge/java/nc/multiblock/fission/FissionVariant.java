package nc.multiblock.fission;

import nc.init.NCFission;
import nc.init.NCPebbleFission;
import nc.init.NCSaltFission;
import nc.recipe.SolidFissionRecipe;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntityType;

public enum FissionVariant {
    SOLID, PEBBLE, SALT;

    static FissionVariant forController(FissionPartBlock.Role role) {
        return switch (role) {
            case PEBBLE_CONTROLLER -> PEBBLE;
            case SALT_CONTROLLER -> SALT;
            default -> SOLID;
        };
    }

    RecipeType<SolidFissionRecipe> itemFuelType() {
        return switch (this) {
            case SOLID -> NCFission.FUEL_TYPE.get();
            case PEBBLE -> NCPebbleFission.FUEL_TYPE.get();
            case SALT -> throw new IllegalStateException("Molten-salt fission uses fluid fuel");
        };
    }

    String itemInventoryKey() { return this == PEBBLE ? "pebble_inventory" : "fuel_inventory"; }
    String itemFilterKey() { return this == PEBBLE ? "pebble_filter" : "fuel_filter"; }
    String itemCircuitsKey() { return this == PEBBLE ? "pebble_fuel" : "item_fuel"; }

    FissionWallBlock controller() {
        return switch (this) {
            case SOLID -> NCFission.CONTROLLER.get();
            case PEBBLE -> NCPebbleFission.CONTROLLER.get();
            case SALT -> NCSaltFission.CONTROLLER.get();
        };
    }

    BlockEntityType<SolidFissionController> entity() {
        return switch (this) {
            case SOLID -> NCFission.CONTROLLER_ENTITY.get();
            case PEBBLE -> NCPebbleFission.CONTROLLER_ENTITY.get();
            case SALT -> NCSaltFission.CONTROLLER_ENTITY.get();
        };
    }

    MenuType<SolidFissionMenu> menu() {
        return switch (this) {
            case SOLID -> NCFission.MENU.get();
            case PEBBLE -> NCPebbleFission.MENU.get();
            case SALT -> NCSaltFission.MENU.get();
        };
    }

    double intrinsicEfficiency() {
        return switch (this) {
            case SOLID -> NCFission.intrinsicFluxEfficiency();
            case PEBBLE -> NCPebbleFission.intrinsicEfficiency();
            case SALT -> NCSaltFission.intrinsicEfficiency();
        };
    }
}
