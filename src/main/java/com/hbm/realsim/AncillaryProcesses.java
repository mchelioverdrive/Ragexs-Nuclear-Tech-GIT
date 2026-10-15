package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded specialty batches. Fictional preparations carry explicit assays, not invented molecular identities. */
public final class AncillaryProcesses {
    private AncillaryProcesses() { }
    private static long m(String name, int count) { return ChemistryProcesses.mass(name, count); }
    private static String item(String field) { return SpecialistProcesses.key(field); }
    private static long ratio(long mass, String numerator, String denominator) {
        return BigDecimal.valueOf(mass).multiply(new BigDecimal(numerator)).divide(new BigDecimal(denominator), 0, RoundingMode.DOWN).longValueExact();
    }
    private static void state(String name, PhysicalState.Phase phase, PhysicalState.Basis basis, String kg, BigDecimal lhv, String reference, long sensible) {
        PhysicalRegistry.adopt(name, new PhysicalState(name.toLowerCase(java.util.Locale.US),phase,basis,
                new BigDecimal(kg).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).setScale(0,RoundingMode.DOWN).longValueExact(),lhv,reference,null,BigDecimal.valueOf(sensible)));
    }
    private static void solid(String field, String kg, String lhv) {
        state(item(field),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,kg,new BigDecimal(lhv),"adopted specialty item packet",0);
    }
    static void registerAssays() {
        solid("powder_ash@4","1","32800000");solid("powder_ash@5",".1","34000000");
        solid("arc_electrode@0","1.1","29818181.81818181818");solid("arc_electrode_burnt@0",".1","0");
        solid("mineral_dust","1","0");solid("lithium_fluoride","1","0");solid("thorium_fluoride","1","0");
        state("FULLERENE",PhysicalState.Phase.LIQUID,PhysicalState.Basis.NOMINAL,".001",new BigDecimal("3400000"),"one gram/count of 10 wt% C60 suspension in recycled ligand; not litres",0);
        state(item("powder_ice"),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,".125",BigDecimal.ZERO,"0 C ice powder relative to 20 C liquid water",-417300); solid("powder_nitan_mix","1","0"); solid("powder_desh_mix","1","0"); solid("ingot_desh","1","0");
        state("MERCURY",PhysicalState.Phase.LIQUID,PhysicalState.Basis.NOMINAL,".008",BigDecimal.ZERO,"125 inventory counts per kg; existing ingot conversion retained, not litres",0);
        BigDecimal coolant=PhysicalRegistry.get("COOLANT").lhvJoulesPerKg;
        BigDecimal cryoLhv=coolant.multiply(BigDecimal.valueOf(m("COOLANT",1))).divide(BigDecimal.valueOf(m("COOLANT",1)+m(item("powder_ice"),1)),18,RoundingMode.DOWN);
        state("CRYOGEL",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,"1",cryoLhv,"fictional refrigerated suspension; 1 kg/reference L, -170 C; paid refrigeration",-500000);
        long nitan=m("KEROSENE",4)+m("MERCURY",16)+m(item("powder_nitan_mix"),1);
        BigDecimal nitanLhv=ProcessRecipe.energy("KEROSENE",m("KEROSENE",4)).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).divide(BigDecimal.valueOf(nitan),18,RoundingMode.DOWN);
        state("NITAN",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,"1",nitanLhv,"fictional fuel formulation; 1 kg/reference L; LHV bounded by kerosene feed",0);
        for(String milk:new String[]{"MILK","EMILK","CMILK","CREAM"}) state(milk,PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,milk.equals("CMILK")?"1.3":milk.equals("CREAM")?".99":"1.03",BigDecimal.ZERO,"food process density; nutrition is not generator fuel",0);
        solid("vanilla.egg",".06","0");solid("strawberry",".02","0");solid("butter",".25","0");state(item("vanilla.cake"),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,"1",ProcessRecipe.energy("SUGAR_ITEM",m("SUGAR_ITEM",1)).divide(new BigDecimal("3.4"),18,RoundingMode.DOWN),"baked batch retains the calibrated sugar energy; other food energy uncalibrated",0);solid("s_cream",".25","0");
        solid("particle_empty",".1","0");
        state(item("particle_helium"),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,
                BigDecimal.valueOf(m(item("particle_empty"),1)+m("HELIUM4",1)).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG),18,RoundingMode.DOWN).toPlainString(),BigDecimal.ZERO,"sealed 1 standard L helium charge plus 100 g vessel",0);
        solid("block.sand_lead","1","0");solid("nuclear_waste_vitrified","1","0");
        state("THORIUM_SALT",PhysicalState.Phase.LIQUID,PhysicalState.Basis.NOMINAL,".001",BigDecimal.ZERO,"1 g salt/count; carrier 90% (LiF:BeF2 2:1 mass), ThF4 10%",0);
        state("THORIUM_SALT_DEPLETED",PhysicalState.Phase.LIQUID,PhysicalState.Basis.NOMINAL,".001",BigDecimal.ZERO,"1 g/count: carrier 90%, ThF4 7%, UF4 1%, fission products 2%",0);
        for(String bromide:new String[]{"URANIUM_BROMIDE","PLUTONIUM_BROMIDE","THORIUM_BROMIDE"}) state(bromide,PhysicalState.Phase.LIQUID,PhysicalState.Basis.NOMINAL,".001",BigDecimal.ZERO,"1 g contained bromide-equivalent/count, not gas or liquid litres",0);
        solid("nugget_u235",".111111111111111111","0");solid("nugget_pu239",".111111111111111111","0");
    }
    private static ProcessRecipe bill(String id,String[] in,int[] q,String[] solids,int[] counts,Map<String,Long> out,int ticks,long work,long heat,int temperature) {
        return SpecialistProcesses.bill(id,in,q,solids,counts,out,ticks,work,heat,temperature);
    }
    public static ProcessRecipe cryogel() {
        Map<String,Long> out=new LinkedHashMap<>();out.put("CRYOGEL",m("COOLANT",1)+m(item("powder_ice"),1));
        // Conservative 0.25 refrigeration COP at the cryogenic endpoint; work becomes rejected heat.
        return bill("cryogenic_suspension",new String[]{"COOLANT"},new int[]{1},new String[]{item("powder_ice")},new int[]{1},out,5000,2000000,0,20);
    }
    public static ProcessRecipe nitan() {
        Map<String,Long> out=new LinkedHashMap<>();out.put("NITAN",m("KEROSENE",4)+m("MERCURY",16)+m(item("powder_nitan_mix"),1));
        return bill("fictional_nitan_formulation",new String[]{"KEROSENE","MERCURY"},new int[]{4,16},new String[]{item("powder_nitan_mix")},new int[]{1},out,2400,120000,0,20);
    }
    public static ProcessRecipe desh() {
        Map<String,Long> out=new LinkedHashMap<>();out.put(item("ingot_desh"),m(item("powder_desh_mix"),1)+m(item("ingot_chromium"),1)+m(item("ingot_vanadium"),1));out.put("ARGON",m("ARGON",20));
        return bill("desh_inert_alloying",new String[]{"ARGON"},new int[]{20},new String[]{item("powder_desh_mix"),item("ingot_chromium"),item("ingot_vanadium")},new int[]{1,1,1},out,2400,200000,6000000,1500);
    }
    public static ProcessRecipe vitrify(boolean gas) {
        String feed=gas?"WASTEGAS":"WASTEFLUID";int amount=gas?10:1000;Map<String,Long> out=new LinkedHashMap<>();out.put(item("nuclear_waste_vitrified"),m(feed,amount)+m(item("block.sand_lead"),1));
        // The gas route represents encapsulation in a sealed sorbent/glass package, not liquefaction.
        return bill(gas?"sealed_radioactive_offgas_package":"waste_glass_immobilization",new String[]{feed},new int[]{amount},new String[]{item("block.sand_lead")},new int[]{1},out,2400,100000,gas?200000:5000000,gas?100:1200);
    }
    public static ProcessRecipe heliumCapsule() {
        Map<String,Long> out=new LinkedHashMap<>();out.put(item("particle_helium"),m(item("particle_empty"),1)+m("HELIUM4",1));
        return bill("helium_vessel_filling",new String[]{"HELIUM4"},new int[]{1},new String[]{item("particle_empty")},new int[]{1},out,400,2000,0,20);
    }
    public static ProcessRecipe saltRefuel() {
        long feed=m("THORIUM_SALT_DEPLETED",1000),makeup=ratio(feed,".03","1"),u=ratio(feed/100,"233.0396355","309.0332483");
        Map<String,Long> out=new LinkedHashMap<>();out.put("THORIUM_SALT",feed);out.put(item("thorium_fluoride"),m(item("thorium_fluoride"),1)-makeup);
        out.put(item("nugget_u233"),u);out.put("FLUORINE",feed/100-u);out.put("WASTEFLUID",feed/50);
        return bill("game_salt_assay_recovery",new String[]{"THORIUM_SALT_DEPLETED"},new int[]{1000},new String[]{item("thorium_fluoride")},new int[]{1},out,2400,300000,1000000,350);
    }
    public static ProcessRecipe fuelFluoride(boolean thorium) {
        String metal=thorium?item("nugget_th232"):"LITHIUM_ITEM",product=item(thorium?"thorium_fluoride":"lithium_fluoride");
        long feed=m(metal,1),pure=ratio(feed,thorium?"80.0253728":"20.0063432",thorium?"232.0380558":"6.941"),solution=ratio(pure,"1",".48"),hydrogen=ratio(feed,thorium?"4.03176":"1.00794",thorium?"232.0380558":"6.941");
        int q=Math.toIntExact(PetroleumProcesses.ceilDiv(solution,m("HYDROFLUORIC_ACID",1)));Map<String,Long> out=new LinkedHashMap<>();out.put(product,feed+pure-hydrogen);out.put("HYDROGEN_GAS",hydrogen);out.put("FRESH_WATER",solution-pure);out.put("HYDROFLUORIC_ACID",m("HYDROFLUORIC_ACID",q)-solution);
        return bill(thorium?"contained_thorium_fluoride":"lithium_fluoride",new String[]{"HYDROFLUORIC_ACID"},new int[]{q},new String[]{metal},new int[]{1},out,2400,100000,thorium?1000000:7000000,350);
    }
    public static ProcessRecipe freshSalt() {
        Map<String,Long> out=new LinkedHashMap<>();out.put("THORIUM_SALT",m(item("lithium_fluoride"),6)+m(item("beryllium_fluoride"),3)+m(item("thorium_fluoride"),1));
        return bill("fluoride_carrier_fuel_salt",new String[0],new int[0],new String[]{item("lithium_fluoride"),item("beryllium_fluoride"),item("thorium_fluoride")},new int[]{6,3,1},out,6000,200000,15000000,650);
    }
    public static ProcessRecipe radioLigand() {
        Map<String,Long> out=new LinkedHashMap<>();out.put("RADIOSOLVENT",m("FRESH_WATER",1)+m(item("mineral_dust"),1));
        return bill("fictional_mineral_complexant_preparation",new String[]{"FRESH_WATER"},new int[]{1},new String[]{item("mineral_dust")},new int[]{1},out,1200,120000,0,20);
    }
    public static ProcessRecipe fullereneSuspension() {
        Map<String,Long> out=new LinkedHashMap<>();out.put("FULLERENE",m("RADIOSOLVENT",9)+m(item("powder_ash@5"),10));
        return bill("fullerene_dispersion",new String[]{"RADIOSOLVENT"},new int[]{9},new String[]{item("powder_ash@5")},new int[]{10},out,2400,120000,0,20);
    }
    public static ProcessRecipe fullereneArc() {
        Map<String,Long> out=new LinkedHashMap<>();out.put(item("powder_ash@5"),m(item("powder_ash@5"),8));out.put(item("powder_ash@4"),m(item("powder_ash@4"),1)/5);out.put(item("arc_electrode_burnt@0"),m(item("arc_electrode_burnt@0"),1));out.put("HELIUM4",m("HELIUM4",20));
        return bill("graphite_arc_condensation",new String[]{"HELIUM4"},new int[]{20},new String[]{item("arc_electrode@0")},new int[]{1},out,150000,60000000,0,20).continuous();
    }
    public static ProcessRecipe fullereneRecovery() {
        long feed=m("FULLERENE",1000);Map<String,Long> out=new LinkedHashMap<>();out.put(item("powder_ash@5"),feed/10);out.put("RADIOSOLVENT",feed-feed/10);
        return bill("fullerene_carrier_recovery",new String[]{"FULLERENE"},new int[]{1000},new String[0],new int[0],out,1200,50000,1000000,80);
    }
    public static ProcessRecipe radiumRecovery() {
        long feed=m(item("radium_residue"),1),radium=feed/100000;Map<String,Long> out=new LinkedHashMap<>();out.put(item("nugget_ra226"),radium);out.put("WASTEFLUID",feed-radium);out.put("SULFURIC_ACID",m("SULFURIC_ACID",1));
        return bill("selected_radium_residue_recovery",new String[]{"SULFURIC_ACID"},new int[]{1},new String[]{item("radium_residue")},new int[]{1},out,2400,500000,1000000,80);
    }
    public static ProcessRecipe bromide(int kind) {
        String metal=item(kind==0?"nugget_u235":kind==1?"nugget_pu239":"nugget_th232"),output=kind==0?"URANIUM_BROMIDE":kind==1?"PLUTONIUM_BROMIDE":"THORIUM_BROMIDE";
        long feed=m(metal,1),br=ratio(feed,kind==1?"239.712":"319.616",kind==0?"235.0439299":kind==1?"239.0521634":"232.0380558");Map<String,Long> out=new LinkedHashMap<>();out.put(output,feed+br);out.put("BROMINE_REAGENT_ITEM",m("BROMINE_REAGENT_ITEM",1)-br);
        return bill("contained_bromide_"+kind,new String[0],new int[0],new String[]{metal,"BROMINE_REAGENT_ITEM"},new int[]{1,1},out,2400,100000,1000000,350);
    }
    public static ProcessRecipe butter() {
        long cream=m("CREAM",1),fat=ratio(cream,".35","1");Map<String,Long> out=new LinkedHashMap<>();out.put(item("butter"),fat);out.put("EMILK",cream-fat);
        return bill("cream_churning",new String[]{"CREAM"},new int[]{1},new String[0],new int[0],out,1200,20000,0,20);
    }
    public static ProcessRecipe cake() {
        long water=m("CMILK",1)/10,total=m("CMILK",1)+m("CREAM",1)+m("SUGAR_ITEM",1)+m(item("vanilla.egg"),4);Map<String,Long> out=new LinkedHashMap<>();out.put(item("vanilla.cake"),total-water);out.put("VENT_STEAM",water);
        return bill("food_batch_baking",new String[]{"CMILK","CREAM"},new int[]{1,1},new String[]{"SUGAR_ITEM",item("vanilla.egg")},new int[]{1,4},out,2400,50000,1600000,180);
    }
    public static ProcessRecipe iceCream() {
        long total=m("CREAM",1)+m(item("butter"),1)+m(item("strawberry"),4);Map<String,Long> out=new LinkedHashMap<>();out.put(item("s_cream"),total);
        return bill("food_refrigeration",new String[]{"CREAM"},new int[]{1},new String[]{item("butter"),item("strawberry")},new int[]{1,4},out,1200,600000,0,20);
    }
    private static final class DairyBill { private static final ProcessRecipe BILL=createDairy(); }
    public static ProcessRecipe dairy() { return DairyBill.BILL; }
    private static ProcessRecipe createDairy() {
        // Adopted milk: 3.5% fat, 8.5% nonfat solids. Cream: 35% fat/6% nonfat solids.
        // Half of the remaining nonfat solids go to 50%-solids condensed skim; the rest to 9%-solids skim.
        long feed=m("MILK",10),cream=feed/10,nonfat=ratio(feed,".085","1")-ratio(cream,".06","1"),condensed=nonfat,skim=ratio(nonfat,".5",".09");
        Map<String,Long> out=new LinkedHashMap<>();out.put("EMILK",skim);out.put("CMILK",condensed);out.put("CREAM",cream);out.put("FRESH_WATER",feed-cream-skim-condensed);
        return bill("dairy_separation_evaporation",new String[]{"MILK"},new int[]{10},new String[0],new int[0],out,3600,180000,13000000,120);
    }
    public static Map<Integer,ProcessRecipe> defaults(){Map<Integer,ProcessRecipe> map=new LinkedHashMap<>();map.put(1034,radiumRecovery());map.put(1075,fullereneArc());map.put(1186,fullereneRecovery());map.put(1187,fuelFluoride(false));map.put(1188,fuelFluoride(true));map.put(1189,freshSalt());map.put(37,cryogel());map.put(38,desh());map.put(39,nitan());map.put(68,vitrify(false));map.put(69,vitrify(true));map.put(1072,heliumCapsule());map.put(119,saltRefuel());map.put(1004,bromide(0));map.put(1005,bromide(1));map.put(1007,bromide(2));map.put(120,cake());map.put(121,butter());map.put(122,iceCream());return map;}
}
