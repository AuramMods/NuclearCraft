package nc.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class GeneratedPackResources extends AbstractPackResources {
    private final PackType type;
    private final Map<ResourceLocation, byte[]> resources;
    private final Set<String> namespaces;

    GeneratedPackResources(String id, PackType type, Map<ResourceLocation, byte[]> resources) {
        super(id, true);
        this.type = type;
        this.resources = resources;
        namespaces = resources.keySet().stream().map(ResourceLocation::getNamespace).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        byte[] bytes = type == this.type ? resources.get(id) : null;
        return bytes == null ? null : () -> new ByteArrayInputStream(bytes);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != this.type) {
            return;
        }
        String prefix = path.isEmpty() ? "" : path + "/";
        resources.forEach((id, bytes) -> {
            if (id.getNamespace().equals(namespace) && id.getPath().startsWith(prefix)) {
                output.accept(id, () -> new ByteArrayInputStream(bytes));
            }
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == this.type ? namespaces : Set.of();
    }

    @Override
    public void close() {}
}
