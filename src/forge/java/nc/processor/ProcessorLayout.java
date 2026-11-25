package nc.processor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public record ProcessorLayout(Point size, Point textureSize, Point player, Point faces, Point config, Point overflow,
                              Point redstone, List<Rect> itemInputs, List<Rect> itemOutputs, List<Rect> fluidInputs,
                              List<Rect> fluidOutputs, Rect speedUpgrade, Rect energyUpgrade, Bar progress, Bar energy,
                              @Nullable ResourceLocation texture, Jei jei) {
    public record Point(int x, int y) {
        public boolean visible() {
            return x >= 0 && y >= 0;
        }
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean visible() {
            return x >= 0 && y >= 0 && width > 0 && height > 0;
        }

        public boolean contains(double mouseX, double mouseY) {
            return visible() && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }

        public int itemX() {
            return x + (width - 16) / 2;
        }

        public int itemY() {
            return y + (height - 16) / 2;
        }
    }

    public record Bar(Rect area, Point sprite) {}

    public record Jei(ResourceLocation id, @Nullable String title, @Nullable ResourceLocation texture, Point textureSize,
                      Rect background, Rect tooltip, Rect clickArea, boolean cropped) {}

    public static ProcessorLayout read(String id, JsonObject definition, int itemIn, int itemOut,
                                       int fluidIn, int fluidOut, boolean upgrades) {
        JsonObject gui = GsonHelper.getAsJsonObject(definition, "layout", new JsonObject());
        int rows = Math.max(1, Math.max(rows(itemIn, fluidIn), rows(itemOut, fluidOut)));
        int extension = rows * 20 - 17 + (upgrades ? 20 : 0);
        Point size = point(gui, "size", new Point(176, 166 + extension));
        Point textureSize = point(gui, "texture_size", new Point(256, Math.max(256, size.y)));
        if (size.x <= 0 || size.y <= 0 || textureSize.x <= 0 || textureSize.y <= 0) {
            throw new JsonSyntaxException("Processor layout and texture sizes must be positive");
        }
        int yOffset = size.y - 166;
        List<Rect> itemInputs = slots(gui, "item_inputs", itemIn, index -> gridSlot(index, 27, 35));
        List<Rect> itemOutputs = slots(gui, "item_outputs", itemOut, index -> gridSlot(index, 111, 35));
        List<Rect> fluidInputs = slots(gui, "fluid_inputs", fluidIn, index -> gridSlot(index, 27, 35 + 20 * ((itemIn + 1) / 2)));
        List<Rect> fluidOutputs = slots(gui, "fluid_outputs", fluidOut, index -> gridSlot(index, 111, 35 + 20 * ((itemOut + 1) / 2)));
        var progress = bar(gui, "progress", new Bar(new Rect(70, 35, 37, 16), new Point(176, 3)));
        var energy = bar(gui, "energy", new Bar(new Rect(8, 6, 16, 45 + yOffset), new Point(-1, -1)));
        var texture = location(definition, "gui_texture", null);

        JsonObject jei = GsonHelper.getAsJsonObject(definition, "jei", new JsonObject());
        ResourceLocation recipeId = location(jei, "id", NuclearCraft.id(id));
        String title = GsonHelper.getAsString(jei, "title", null);
        boolean cropped = jei.has("background");
        var jeiTexture = location(jei, "texture", cropped ? texture : null);
        var jeiSize = point(jei, "texture_size", textureSize);
        var crop = rect(jei, "background", new Rect(0, 23, size.x, Math.max(48, 48 + yOffset)));
        if (!crop.visible() || jeiSize.x <= 0 || jeiSize.y <= 0) {
            throw new JsonSyntaxException("JEI background and texture sizes must be positive");
        }
        var jeiLayout = new Jei(recipeId, title, jeiTexture, jeiSize, crop,
                rect(jei, "tooltip", progress.area), rect(jei, "click_area", progress.area), cropped);
        return new ProcessorLayout(size, textureSize, point(gui, "player", new Point(8, 84 + yOffset)),
                point(gui, "faces", new Point(27, 53 + yOffset)), point(gui, "config", new Point(27, 17)),
                point(gui, "overflow", new Point(136, 8)), point(gui, "redstone", new Point(151, 34)),
                itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                rect(gui, "speed_upgrade", new Rect(132, 33 + yOffset, 16, 16)),
                rect(gui, "energy_upgrade", new Rect(152, 33 + yOffset, 16, 16)),
                progress, energy, texture, jeiLayout);
    }

    private static int rows(int items, int fluids) {
        return (items + 1) / 2 + (fluids + 1) / 2;
    }

    private static Rect gridSlot(int index, int x, int y) {
        return new Rect(x + 20 * (index % 2), y + 20 * (index / 2), 16, 16);
    }

    private static List<Rect> slots(JsonObject json, String key, int count, IntFunction<Rect> fallback) {
        List<Rect> slots = new ArrayList<>(count);
        if (json.has(key)) {
            var entries = GsonHelper.getAsJsonArray(json, key);
            if (entries.size() != count) {
                throw new JsonSyntaxException("Expected " + count + " positions for " + key);
            }
            for (var entry : entries) {
                var rect = rect(coordinates(GsonHelper.convertToJsonArray(entry, key), key, 4));
                if (!rect.visible()) {
                    throw new JsonSyntaxException("A slot in " + key + " must have nonnegative coordinates and positive dimensions");
                }
                slots.add(rect);
            }
        } else {
            for (int index = 0; index < count; index++) {
                slots.add(fallback.apply(index));
            }
        }
        return List.copyOf(slots);
    }

    private static Point point(JsonObject json, String key, Point fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        int[] point = coordinates(GsonHelper.getAsJsonArray(json, key), key, 2);
        return new Point(point[0], point[1]);
    }

    private static Rect rect(JsonObject json, String key, Rect fallback) {
        return json.has(key) ? rect(coordinates(GsonHelper.getAsJsonArray(json, key), key, 4)) : fallback;
    }

    private static Rect rect(int[] coordinates) {
        return new Rect(coordinates[0], coordinates[1], coordinates[2], coordinates[3]);
    }

    private static Bar bar(JsonObject json, String key, Bar fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        int[] coords = coordinates(GsonHelper.getAsJsonArray(json, key), key, 6);
        return new Bar(rect(coords), new Point(coords[4], coords[5]));
    }

    private static int[] coordinates(JsonArray array, String key, int count) {
        if (array.size() != count) {
            throw new JsonSyntaxException("Expected " + count + " coordinates for " + key);
        }
        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            double value = GsonHelper.convertToDouble(array.get(index), key);
            if (value != (int) value || value < -1 || value > 4096) {
                throw new JsonSyntaxException("Invalid coordinate in " + key);
            }
            result[index] = (int) value;
        }
        return result;
    }

    private static @Nullable ResourceLocation location(JsonObject json, String key, @Nullable ResourceLocation fallback) {
        if (!json.has(key)) {
            return fallback;
        }
        String value = GsonHelper.getAsString(json, key);
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new JsonSyntaxException("Invalid resource location for " + key + ": " + value);
        }
        return location;
    }
}
