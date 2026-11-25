package nc.script;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import nc.NuclearCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public final class ScriptAddons {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String MARKER = "DONT_PUT_YOUR_SCRIPTS_IN_HERE";
    private static final String INDEX = ".addons";
    private static final String PACK = ".pack";
    private static final Set<String> CLIENT_ROOTS = Set.of("blockstates", "lang", "models", "textures",
            "patchouli_books", "sounds", "shaders", "font");
    private static final Set<String> SERVER_ROOTS = Set.of("advancements", "loot_tables", "recipes", "tags",
            "structures", "worldgen");
    private static final Set<String> MOD_NAMESPACES = Set.of("qmd", "trinity");
    private static final Set<String> DISABLED_SUFFIXES = Set.of(".ignore", ".ignored", ".disable", ".disabled");
    private static final Pattern VERSION = Pattern.compile("-\\d+(?:[._-][0-9a-z]+)*$");
    private static volatile Set<String> loaded = Set.of();

    private ScriptAddons() {}

    public static Path packRoot() {
        return generated(FMLPaths.GAMEDIR.get()).resolve(PACK);
    }

    public static void initialize() {
        Path game = FMLPaths.GAMEDIR.get();
        Path installed = generated(game);
        if (!Files.exists(input(game), LinkOption.NOFOLLOW_LINKS)
                && !Files.exists(installed.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            if (managed(installed) && Files.isRegularFile(installed.resolve(INDEX), LinkOption.NOFOLLOW_LINKS)) {
                loaded = Set.copyOf(Files.readAllLines(installed.resolve(INDEX)));
            }
            rebuild(game);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Could not rebuild NuclearCraft script addons during startup", failure);
        }
    }

    public static boolean isLoaded(String id) {
        return loaded.contains(id.toLowerCase(Locale.ROOT));
    }

    public static synchronized int rebuild(Path game) throws IOException {
        Path input = input(game);
        Files.createDirectories(input);
        Path installed = generated(game);
        if (Files.exists(installed, LinkOption.NOFOLLOW_LINKS) && !managed(installed)) {
            throw new IOException("The generated addon path already exists without its ownership marker: " + installed);
        }
        Files.createDirectories(installed.getParent());
        Path staging = Files.createTempDirectory(game, ".nuclearcraft-addons-");
        Path backup = staging.resolve("previous");
        boolean retainBackup = false;
        try {
            Path next = Files.createDirectory(staging.resolve("next"));
            var addon = new Reconstruction(next);
            addon.read(input);
            addon.finish();
            if (Files.exists(installed, LinkOption.NOFOLLOW_LINKS)) {
                Files.move(installed, backup);
            }
            try {
                Files.move(next, installed);
            } catch (IOException failure) {
                if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
                    try {
                        Files.move(backup, installed);
                    } catch (IOException restoreFailure) {
                        retainBackup = true;
                        failure.addSuppressed(restoreFailure);
                        LOGGER.error("The previous NuclearCraft script addons are in {}", backup, restoreFailure);
                    }
                }
                throw failure;
            }
            loaded = Set.copyOf(addon.ids);
            return loaded.size();
        } finally {
            if (!retainBackup) {
                try {
                    deleteTree(staging);
                } catch (IOException failure) {
                    LOGGER.warn("Could not clean the temporary NuclearCraft addon directory {}", staging, failure);
                }
            }
        }
    }

    private static Path input(Path game) {
        return game.resolve("resources/nuclearcraft/addons");
    }

    private static Path generated(Path game) {
        return game.resolve("scripts/nc_script_addons");
    }

    private static boolean managed(Path path) {
        return Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(path.resolve(MARKER), LinkOption.NOFOLLOW_LINKS);
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static String name(Path path) {
        return path.getFileName().toString();
    }

    private static boolean enabled(Path path) {
        String filename = name(path).toLowerCase(Locale.ROOT);
        return !filename.startsWith(".") && !filename.equals("__macosx")
                && DISABLED_SUFFIXES.stream().noneMatch(filename::endsWith);
    }

    private static List<Path> children(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            return paths.filter(ScriptAddons::enabled).sorted(Comparator.comparing(ScriptAddons::name)).toList();
        }
    }

    private static List<Path> files(Path directory) throws IOException {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var paths = Files.walk(directory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> {
                        for (Path part : directory.relativize(path)) {
                            if (!enabled(part)) {
                                return false;
                            }
                        }
                        return true;
                    }).sorted().toList();
        }
    }

    private static String relative(Path root, Path file) {
        var parts = new ArrayList<String>();
        root.relativize(file).forEach(part -> parts.add(part.toString()));
        return String.join("/", parts);
    }

    private static final class Reconstruction {
        private final Path output;
        private final Set<String> ids = new TreeSet<>();
        private final Map<ResourceLocation, JsonObject> languages = new TreeMap<>();

        private Reconstruction(Path output) {
            this.output = output;
        }

        private void read(Path source) throws IOException {
            for (Path candidate : children(source)) {
                if (Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    readDirectory(candidate, name(candidate));
                    continue;
                }
                String filename = name(candidate).toLowerCase(Locale.ROOT);
                if (Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)
                        && (filename.endsWith(".zip") || filename.endsWith(".jar"))) {
                    try (var archive = FileSystems.newFileSystem(candidate)) {
                        readDirectory(archive.getPath("/"), filename.substring(0, filename.length() - 4));
                    } catch (IOException | RuntimeException failure) {
                        throw new IOException("Could not read script addon archive " + candidate, failure);
                    }
                }
            }
        }

        private void readDirectory(Path directory, String name) throws IOException {
            List<Path> folders = children(directory).stream()
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)).toList();
            if (folders.stream().noneMatch(folder -> addonFolder(name(folder)))) {
                for (Path folder : folders) {
                    readDirectory(folder, name(folder));
                }
                return;
            }

            String id = VERSION.matcher(name.toLowerCase(Locale.ROOT)).replaceFirst("")
                    .replaceAll("[^a-z0-9_.-]", "_");
            Path scriptOutput = output.resolve(id).normalize();
            if (id.isEmpty() || id.equalsIgnoreCase(MARKER) || scriptOutput.equals(output) || !scriptOutput.startsWith(output)) {
                throw new IOException("Invalid script addon name " + name);
            }
            ids.add(id);
            copyScripts(directory.resolve("scripts"), scriptOutput);
            for (Path folder : folders) {
                String folderName = name(folder);
                if (CLIENT_ROOTS.contains(folderName) || SERVER_ROOTS.contains(folderName)) {
                    copyLegacy(NuclearCraft.MOD_ID, folder);
                } else if (MOD_NAMESPACES.contains(folderName)) {
                    for (Path component : children(folder)) {
                        copyLegacy(folderName, component);
                    }
                }
            }
            copyPack(directory, PackType.CLIENT_RESOURCES);
            copyPack(directory, PackType.SERVER_DATA);
        }

        private static boolean addonFolder(String name) {
            return CLIENT_ROOTS.contains(name) || SERVER_ROOTS.contains(name) || MOD_NAMESPACES.contains(name)
                    || name.equals("scripts") || name.equals("assets") || name.equals("data");
        }

        private static void copyScripts(Path folder, Path destination) throws IOException {
            for (Path script : files(folder)) {
                Path target = destination.resolve(relative(folder, script)).normalize();
                if (!target.startsWith(destination)) {
                    throw new IOException("Script is outside its addon directory: " + script);
                }
                copy(script, target);
            }
        }

        private void copyLegacy(String namespace, Path folder) throws IOException {
            String category = name(folder);
            if (!CLIENT_ROOTS.contains(category) && !SERVER_ROOTS.contains(category)) {
                return;
            }
            for (Path file : files(folder)) {
                String path = category + "/" + relative(folder, file);
                boolean data = SERVER_ROOTS.contains(category)
                        || category.equals("patchouli_books") && path.endsWith("/book.json");
                copyResource(data ? PackType.SERVER_DATA : PackType.CLIENT_RESOURCES, namespace, path, file);
            }
        }

        private void copyPack(Path directory, PackType type) throws IOException {
            Path root = directory.resolve(type.getDirectory());
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            for (Path namespace : children(root)) {
                for (Path file : files(namespace)) {
                    copyResource(type, name(namespace), relative(namespace, file), file);
                }
            }
        }

        private void copyResource(PackType type, String namespace, String path, Path file) throws IOException {
            if (path.endsWith(".zs")) {
                return;
            }
            ResourceLocation id = ResourceLocation.tryBuild(namespace, path);
            if (id == null) {
                throw new IOException("Invalid resource name " + namespace + ":" + path + " in " + file);
            }
            if (type == PackType.CLIENT_RESOURCES && path.startsWith("lang/")) {
                if (path.endsWith(".lang") || path.endsWith(".json")) {
                    mergeLanguage(id, file);
                    return;
                }
            }
            copy(file, resource(type, id));
        }

        private void mergeLanguage(ResourceLocation id, Path file) throws IOException {
            String path = id.getPath();
            boolean legacy = path.endsWith(".lang");
            if (legacy) {
                id = new ResourceLocation(id.getNamespace(), path.substring(0, path.length() - 5) + ".json");
            }
            JsonObject entries = languages.computeIfAbsent(id, ignored -> new JsonObject());
            if (legacy) {
                for (String line : Files.readAllLines(file)) {
                    String text = line.startsWith("\ufeff") ? line.substring(1) : line;
                    int separator = text.indexOf('=');
                    if (separator > 0 && !text.stripLeading().startsWith("#")) {
                        entries.addProperty(text.substring(0, separator), text.substring(separator + 1));
                    }
                }
                return;
            }
            try (var reader = Files.newBufferedReader(file)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> entries.add(entry.getKey(), entry.getValue()));
            } catch (JsonParseException | IllegalStateException failure) {
                throw new IOException("Invalid translation file " + file, failure);
            }
        }

        private Path resource(PackType type, ResourceLocation id) {
            return output.resolve(PACK).resolve(type.getDirectory()).resolve(id.getNamespace()).resolve(id.getPath());
        }

        private void finish() throws IOException {
            for (var entry : languages.entrySet()) {
                Path destination = resource(PackType.CLIENT_RESOURCES, entry.getKey());
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, entry.getValue().toString());
            }
            Files.createDirectories(output.resolve(PACK));
            Files.createFile(output.resolve(MARKER));
            Files.write(output.resolve(INDEX), ids);
        }

        private static void copy(Path source, Path destination) throws IOException {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
