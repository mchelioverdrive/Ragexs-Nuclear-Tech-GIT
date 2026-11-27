package com.hbm.world.gen.structure;

import com.hbm.config.SettlementConfig.Values;

public enum SettlementKind {
    RESTAURANT("mcdonalds", "classic", "modern", "roadside"),
    STORE("walmart", "market", "supercenter", "warehouse"),
    DATA_CENTER("data_center", "small", "medium", "large"),
    FARM("farm", "wheat", "vegetables", "livestock", "industrial", "orchard"),
    HOUSE("suburban_house", "ranch", "two_story", "brick", "attached_garage", "detached_garage", "basement"),
    ROAD("road", "straight", "curve", "three_way", "four_way", "slope", "dead_end", "cul_de_sac");

    private final String id;
    private final String[] variants;

    SettlementKind(String id, String... variants) {
        this.id = id;
        this.variants = variants;
    }

    public String wandName() { return "structure_wand_" + id; }
    public int variantCount() { return variants.length; }
    public String variantKey(int variant) { return "settlement.variant." + id + "." + variants[variant]; }

    String variantName(int variant) { return id + "/" + variants[variant]; }

    int smallerVariant(int variant) {
        if(this == DATA_CENTER && variant > 0) return variant - 1;
        if((this == FARM && variant == 3) || (this == HOUSE && (variant == 4 || variant == 5))) return 0;
        return -1;
    }

    int frequency(Values config) {
        switch(this) {
        case RESTAURANT: return config.restaurants ? config.restaurantRarity : 0;
        case STORE: return config.stores ? config.storeRarity : 0;
        case DATA_CENTER: return config.dataCenters ? config.dataCenterRarity : 0;
        case FARM: return config.farms ? config.farmRarity : 0;
        case HOUSE: return config.houses ? config.houseRarity : 0;
        default: return 0;
        }
    }

    int halfWidth(int variant) {
        switch(this) {
        case RESTAURANT: return 17;
        case STORE: return 26;
        case DATA_CENTER: return 14 + variant * 5;
        case FARM: return variant == 3 ? 24 : 17;
        case HOUSE: return variant == 4 ? 13 : 11;
        default: throw new IllegalStateException("Roads have component-specific bounds");
        }
    }

    int depth(int variant) {
        switch(this) {
        case RESTAURANT: return 34;
        case STORE: return 51;
        case DATA_CENTER: return 28 + variant * 6;
        case FARM: return variant == 3 ? 48 : 34;
        case HOUSE: return 26;
        default: throw new IllegalStateException("Roads have component-specific bounds");
        }
    }
}
