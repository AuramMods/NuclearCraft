package nc.init;

import net.minecraft.sounds.SoundEvents;

final class NCChemicalFluids {
    private NCChemicalFluids() {}

    static void register() {
        NCFluids.molten("alugentum", 0xB5C9CB);
        NCFluids.molten("aluminum_sulfide", 0xBEFFA2);
        NCFluids.liquid("ammonium_sulfate_solution", "salt_solution", 1000, 0x4D73B5, 300, 1000);
        NCFluids.molten("baalo", 0x857958);
        NCFluids.molten("bacro", 0x5E615E);
        NCFluids.molten("bacro_nio", 0x414641);
        NCFluids.molten("barium", 0x4B4B4B);
        NCFluids.molten("barium_oxide", 0xC7D4D6);
        NCFluids.molten("barium_sulfide", 0xBDA776);
        NCFluids.molten("bas", 0x9B9B89);
        NCFluids.liquid("benzene", "liquid", 800, 0x999999, 300, 800);
        NCFluids.liquid("borax_solution", "salt_solution", 1000, 0x8E98F1, 300, 1000);
        NCFluids.liquid("boric_acid", "liquid", 1000, 0x696939, 300, 1000);
        NCFluids.liquid("boron_nitride_solution", "salt_solution", 1000, 0x4F68A8, 300, 1000);
        NCFluids.liquid("calcium_sulfate_solution", "salt_solution", 1000, 0x7379CD, 300, 1000);
        NCFluids.gas("chlorine", 0xFFD4DB62, 300);
        NCFluids.gas("depleted_hydrogen_sulfide", 0xFF59514E, 300);
        NCFluids.molten("dfdps", 0xB4B3A7);
        NCFluids.liquid("difluorobenzene", "liquid", 800, 0x8CB57B, 300, 800);
        NCFluids.liquid("dimethyldifluorosilane", "liquid", 800, 0xAEAF80, 300, 800);
        NCFluids.liquid("dysprholminite_water", "salt_solution", 1000, 0x3A4284, 300, 1000);
        NCFluids.liquid("fluorite_water", "salt_solution", 1000, 0x5C7BC3, 300, 1000);
        NCFluids.liquid("fluorobenzene", "liquid", 800, 0xBAB58B, 300, 800);
        NCFluids.gas("fso_vapor", 0xFF8C862E, 1000);
        NCFluids.molten("gadolinium", 0x99C3F2);
        NCFluids.liquid("hodybeso_solution", "salt_solution", 1000, 0x7C7EA1, 300, 1000);
        NCFluids.gas("hydrogen_chloride", 0xFFBCC371, 300);
        NCFluids.liquid("hydrogen_peroxide", "hydrogen_peroxide", 1000, 0xFFFFFF, 300, 1000);
        NCFluids.gas("hydrogen_sulfide", 0xFF785830, 300);
        NCFluids.liquid("hydroquinone_solution", "salt_solution", 1000, 0x737DD5, 300, 1000);
        NCFluids.liquid("irradiated_borax_solution", "salt_solution", 1000, 0x9789CB, 300, 1000);
        NCFluids.molten("manganese_dioxide", 0x28211E);
        NCFluids.liquid("methanol", "liquid", 800, 0x71524C, 300, 800);
        NCFluids.soul("mysterious_soul", 0xFF985CA4, SoundEvents.GHAST_AMBIENT, SoundEvents.GHAST_WARN);
        NCFluids.molten("nickel_oxide", 0x435E49);
        NCFluids.molten("nickel_sulfide", 0xCBD3AD);
        NCFluids.liquid("orthosilicic_acid", "liquid", 1000, 0xB8B8B8, 300, 1000);
        NCFluids.molten("palladium", 0x767676);
        NCFluids.molten("samarium", 0xB0ABB3);
        NCFluids.gas("oxygen_difluoride", 0xFFEA1B01, 300);
        NCFluids.liquid("phenol", "liquid", 800, 0xF2F2F2, 300, 800);
        NCFluids.molten("polydimethylsilylene", 0x774F60);
        NCFluids.molten("polymethylsilylene_methylene", 0x5A5246);
        NCFluids.molten("polyethersulfone", 0xC9B8A6);
        NCFluids.molten("polyphenylene_sulfide", 0x3F3D3E);
        NCFluids.molten("polytetrafluoroethene", 0x7F9F4D);
        NCFluids.liquid("potassium_fluoride_solution", "salt_solution", 1000, 0x7886C8, 300, 1000);
        NCFluids.liquid("potassium_hydroquinone_solution", "salt_solution", 1000, 0x7A86C4, 300, 1000);
        NCFluids.liquid("potassium_hydroxide_solution", "salt_solution", 1000, 0x7384D2, 300, 1000);
        NCFluids.molten("potassium_sulfide", 0x917C34);
        NCFluids.gas("sic_vapor", 0xFF78746A, 1000);
        NCFluids.molten("silicon", 0x676767);
        NCFluids.liquid("sodium_fluoride_solution", "salt_solution", 1000, 0x787ACA, 300, 1000);
        NCFluids.liquid("sodium_hydroquinone_solution", "salt_solution", 1000, 0x7C7ED7, 300, 1000);
        NCFluids.liquid("sodium_hydroxide_solution", "salt_solution", 1000, 0x787DD7, 300, 1000);
        NCFluids.molten("sodium_sulfide", 0x9A8B0B);
        NCFluids.soul("soul", 0xFF7B6F68, SoundEvents.GHAST_HURT, SoundEvents.GHAST_SCREAM);
        NCFluids.liquid("sulfuric_acid", "liquid", 1000, 0x454500, 300, 1000);
        NCFluids.gas("tetrafluoroethene", 0xFF7EA542, 300);
        NCFluids.molten("zirconium", 0xE0E0B8);
    }
}
