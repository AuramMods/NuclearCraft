package nc.init;

final class NCFissionFluids {
    private NCFissionFluids() {}

    static void registerFuels() {
        NCFluids.fission("uranium", 0x375437);
        NCFluids.fission("strontium_90", 0xB8BE88);
        NCFluids.fission("ruthenium_106", 0xA3A3A3);
        NCFluids.fission("caesium_137", 0xADADAD);
        NCFluids.fission("promethium_147", 0x96C199);
        NCFluids.fission("europium_155", 0x74664A);

        fuel("uranium_233", 0x212E20);
        fuel("uranium_235", 0x102D10);
        fuel("uranium_238", 0x333E32);
        fuel("neptunium_236", 0x293E40);
        fuel("neptunium_237", 0x2E3A42);
        fuel("plutonium_238", 0x999999);
        fuel("plutonium_239", 0x9E9E9E);
        fuel("plutonium_241", 0x909090);
        fuel("plutonium_242", 0xABABAB);
        fuel("americium_241", 0x393725);
        fuel("americium_242", 0x332D1A);
        fuel("americium_243", 0x2C280C);
        fuel("curium_243", 0x311137);
        fuel("curium_245", 0x2D102D);
        fuel("curium_246", 0x3F2442);
        fuel("curium_247", 0x321635);
        fuel("berkelium_247", 0x5A2301);
        fuel("berkelium_248", 0x602502);
        fuel("californium_249", 0x460D12);
        fuel("californium_250", 0x3E0C14);
        fuel("californium_251", 0x3B0B16);
        fuel("californium_252", 0x430B0E);

        fuel("tbu", 0x272727);
        fuel("leu_233", 0x1D321B);
        fuel("heu_233", 0x123B0D);
        fuel("leu_235", 0x1D321B);
        fuel("heu_235", 0x123B0D);
        fuel("len_236", 0x1B3132);
        fuel("hen_236", 0x0D393B);
        fuel("lep_239", 0x949494);
        fuel("hep_239", 0x8A8A8A);
        fuel("lep_241", 0x969696);
        fuel("hep_241", 0x8C8C8C);
        fuel("mix_239", 0x485547);
        fuel("mix_241", 0x404E3E);
        fuel("lea_242", 0x322C1B);
        fuel("hea_242", 0x3B2F0D);
        fuel("lecm_243", 0x301B32);
        fuel("hecm_243", 0x360D3B);
        fuel("lecm_245", 0x2D1A2D);
        fuel("hecm_245", 0x340B2E);
        fuel("lecm_247", 0x331F35);
        fuel("hecm_247", 0x39103E);
        fuel("leb_248", 0x4A1B04);
        fuel("heb_248", 0x571B00);
        fuel("lecf_249", 0x3D0310);
        fuel("hecf_249", 0x4A0212);
        fuel("lecf_251", 0x3A0313);
        fuel("hecf_251", 0x460215);
        fuel("depleted_tbu", 0x1D3826);
        fuel("depleted_leu_233", 0x5C5C53);
        fuel("depleted_heu_233", 0x34484B);
        fuel("depleted_leu_235", 0x3A493D);
        fuel("depleted_heu_235", 0x4D5352);
        fuel("depleted_len_236", 0x4E4E46);
        fuel("depleted_hen_236", 0x646663);
        fuel("depleted_lep_239", 0x5C545D);
        fuel("depleted_hep_239", 0x392D2F);
        fuel("depleted_lep_241", 0x473441);
        fuel("depleted_hep_241", 0x40213A);
        fuel("depleted_mix_239", 0x2F342C);
        fuel("depleted_mix_241", 0x2F342F);
        fuel("depleted_lea_242", 0x311830);
        fuel("depleted_hea_242", 0x391C28);
        fuel("depleted_lecm_243", 0x441F1F);
        fuel("depleted_hecm_243", 0x401920);
        fuel("depleted_lecm_245", 0x49221A);
        fuel("depleted_hecm_245", 0x462119);
        fuel("depleted_lecm_247", 0x431512);
        fuel("depleted_hecm_247", 0x441415);
        fuel("depleted_leb_248", 0x471B22);
        fuel("depleted_heb_248", 0x481F26);
        fuel("depleted_lecf_249", 0x4B1B23);
        fuel("depleted_hecf_249", 0x491A22);
        fuel("depleted_lecf_251", 0x4A1B23);
        fuel("depleted_hecf_251", 0x4B1B23);
    }

    private static void fuel(String name, int color) {
        NCFluids.fission(name, color);
        NCFluids.fission(name + "_fluoride", blendWithSalt(color, 0xD3C85D, 7, 8));
        NCFluids.fission(name + "_fluoride_flibe", blendWithSalt(color, 0xC1C8B0, 3, 5));
    }

    private static int blendWithSalt(int base, int salt, int baseWeight, int totalWeight) {
        int color = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            int baseChannel = (base >> shift) & 255;
            int saltChannel = (salt >> shift) & 255;
            color |= ((baseWeight * baseChannel + (totalWeight - baseWeight) * saltChannel) / totalWeight) << shift;
        }
        return color;
    }
}
