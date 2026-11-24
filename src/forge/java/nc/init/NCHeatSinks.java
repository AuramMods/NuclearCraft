package nc.init;

public enum NCHeatSinks {
    WATER("water", 55, 0),
    IRON("iron", 50, 0),
    REDSTONE("redstone", 85, 7),
    QUARTZ("quartz", 80, 0),
    OBSIDIAN("obsidian", 70, 0),
    NETHER_BRICK("nether_brick", 105, 0),
    GLOWSTONE("glowstone", 90, 15),
    LAPIS("lapis", 100, 0),
    GOLD("gold", 110, 0),
    PRISMARINE("prismarine", 115, 0),
    SLIME("slime", 145, 0),
    END_STONE("end_stone", 65, 0),
    PURPUR("purpur", 95, 0),
    DIAMOND("diamond", 200, 0),
    EMERALD("emerald", 195, 0),
    COPPER("copper", 75, 0),
    TIN("tin", 120, 0),
    LEAD("lead", 60, 0),
    BORON("boron", 160, 0),
    LITHIUM("lithium", 130, 0),
    MAGNESIUM("magnesium", 125, 0),
    MANGANESE("manganese", 150, 0),
    ALUMINUM("aluminum", 175, 0),
    SILVER("silver", 170, 0),
    FLUORITE("fluorite", 165, 0),
    VILLIAUMITE("villiaumite", 180, 0),
    CAROBBIITE("carobbiite", 140, 0),
    ARSENIC("arsenic", 135, 0),
    LIQUID_NITROGEN("liquid_nitrogen", 185, 0),
    LIQUID_HELIUM("liquid_helium", 190, 0),
    ENDERIUM("enderium", 155, 0),
    CRYOTHEUM("cryotheum", 205, 0);

    private final String material;
    private final int defaultCooling;
    private final int light;

    NCHeatSinks(String material, int defaultCooling, int light) {
        this.material = material;
        this.defaultCooling = defaultCooling;
        this.light = light;
    }

    public String material() { return material; }
    public String id() { return material + "_heat_sink"; }
    int defaultCooling() { return defaultCooling; }
    int light() { return light; }
}
