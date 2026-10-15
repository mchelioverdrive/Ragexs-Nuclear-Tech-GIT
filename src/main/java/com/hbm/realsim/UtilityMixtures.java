package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Process slurries and explicitly fictional working preparations; retained matter includes the carrier. */
public final class UtilityMixtures {
    private UtilityMixtures() { }
    private static String item(String name) { return SpecialistProcesses.key(name); }
    private static long m(String name,int n) { return ChemistryProcesses.mass(name,n); }
    private static long r(long mass,String a,String b) { return BigDecimal.valueOf(mass).multiply(new BigDecimal(a)).divide(new BigDecimal(b),0,RoundingMode.DOWN).longValueExact(); }
    private static int q(String name,long mass) { return Math.toIntExact(PetroleumProcesses.ceilDiv(mass,m(name,1))); }
    private static void state(String name,String kg,String lhv,String description) { PhysicalRegistry.adopt(name,new PhysicalState(name,PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,new BigDecimal(kg).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).longValueExact(),new BigDecimal(lhv),description,null)); }
    static void registerAssays() {
        state("BLOOD","1.06","0","biological nonfuel working liquid; nutrient energy not calibrated");state("SEEDSLURRY","1","0","wet biological nonfuel slurry; nutrient energy not calibrated");state("SALIENT","1.03","0","fictional 1:1 volume biological blend, not a new chemical species");
        state("EGG","1.06","0","1 kg recycled ligand plus 60 g whole egg per reference L; food energy uncalibrated");
        state("SAS3","1.2","0","fictional acid precursor; no chemical fuel credit");state("SCHRABIDIC","1.2","0","fictional charged oxidizing liquor; carrier and matrix retained");
        state("SMILK","1.03","0","fruit/milk food mixture; nutrient energy not calibrated");
        BigDecimal energy=ProcessRecipe.energy("LUBRICANT",m("LUBRICANT",1));long mass=m("LUBRICANT",1)+m("FRESH_WATER",1);
        PhysicalRegistry.adopt("ELBOWGREASE",new PhysicalState("cleaning_emulsion",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,mass/2,energy.multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).divide(BigDecimal.valueOf(mass),18,RoundingMode.DOWN),"1:1 reference-volume oil/water emulsion, no synthesis from gases",null));
        state("HTCO4","1.2","0","30 wt% contained pertechnetic acid aqueous process liquor");state("MINSOL","1.5","0","2:1 mineral/water mass slurry, not an acid solution");state("REDMUD","1.4","0","10 kg Duna fines + 4 kg water per ten reference L; no free iron output");
        state("DICYANOACETYLENE","1","30000000","25 C reference liquid; adopted LHV, endothermic chemical formation is paid separately");state("SCUTTERBLOOD","1.25","0","fictional retained acid/tissue extract; includes all feed carrier");
        for(String name:new String[]{"block.ore_mineral","block.duna_sands"}) SpecialistProcesses.solid(name,"10");
        SpecialistProcesses.solid("scuttertail",".1");
        PhysicalRegistry.adopt("GLASS_PANE_ITEM",new PhysicalState("thin_glass_pane",PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,PhysicalState.MASS_UNITS_PER_KG/10,BigDecimal.ZERO,"adopted fabrication pane parcel",null));
        PhysicalRegistry.adopt("CARBON_WIRE_ITEM",new PhysicalState("carbon_wire",PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,PhysicalState.MASS_UNITS_PER_KG/8,new BigDecimal("32800000"),"native fine wire = one eighth ingot-equivalent",null));
        long tube=m("GLASS_PANE_ITEM",1)+m("CARBON_WIRE_ITEM",1)+m("POLYMER_ITEM",1)+m("NEON",1);
        PhysicalRegistry.adopt(item("circuit@0"),new PhysicalState("sealed_neon_tube",PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,tube/8,
                ProcessRecipe.energy("CARBON_WIRE_ITEM",m("CARBON_WIRE_ITEM",1)).add(ProcessRecipe.energy("POLYMER_ITEM",m("POLYMER_ITEM",1))).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).divide(BigDecimal.valueOf(tube),18,RoundingMode.DOWN),"eight tubes per 1 standard L neon charge plus weighed enclosure",null));
        PhysicalRegistry.adopt("CHOLESTEROL",new PhysicalState("cholesterol_equivalent",PhysicalState.Phase.LIQUID,PhysicalState.Basis.NOMINAL,PhysicalState.MASS_UNITS_PER_KG/1000,BigDecimal.ZERO,"one gram recovered sterol-equivalent/count; biological energy remains uncalibrated",null));
        PhysicalRegistry.adopt("VENT_NOX",new PhysicalState("nitrogen_dioxide",PhysicalState.Phase.GAS,PhysicalState.Basis.STANDARD_GAS_LITER,46005500000L,BigDecimal.ZERO,"explicit oxidizing-process rejected offgas",null));
        SpecialistProcesses.solid("nugget_technetium",".111111111111111111");
    }
    private static ProcessRecipe bill(String id,String[] fluids,int[] qty,String[] solids,int[] counts,Map<String,Long> out,int ticks,long work,long heat,int temperature) {
        return SpecialistProcesses.bill(id,fluids,qty,solids,counts,out,ticks,work,heat,temperature);
    }
    public static ProcessRecipe blend(String id,String[] fluids,int[] qty,String[] solids,int[] counts) {
        long mass=0;for(int i=0;i<fluids.length;i++)mass+=m(fluids[i],qty[i]);for(int i=0;i<solids.length;i++)mass+=m(solids[i],counts[i]);Map<String,Long> out=new LinkedHashMap<>();out.put(id,mass);
        return bill("working_preparation_"+id,fluids,qty,solids,counts,out,2400,120000,0,20);
    }
    public static Map<String,ProcessRecipe> mixers() {
        Map<String,ProcessRecipe> map=new LinkedHashMap<>();
        map.put("SALIENT",blend("SALIENT",new String[]{"SEEDSLURRY","BLOOD"},new int[]{1,1},new String[0],new int[0]));
        map.put("EGG",blend("EGG",new String[]{"RADIOSOLVENT"},new int[]{1},new String[]{item("vanilla.egg")},new int[]{1}));
        map.put("SCHRABIDIC",blend("SCHRABIDIC",new String[]{"SAS3","PEROXIDE"},new int[]{4,3},new String[]{"CHARGED_MATRIX_ITEM"},new int[]{1}));
        map.put("SMILK",blend("SMILK",new String[]{"MILK"},new int[]{1},new String[]{item("strawberry")},new int[]{4}));
        map.put("ELBOWGREASE",blend("ELBOWGREASE",new String[]{"LUBRICANT","FRESH_WATER"},new int[]{1,1},new String[0],new int[0]));
        map.put("MINSOL",blend("MINSOL",new String[]{"FRESH_WATER"},new int[]{5},new String[]{item("block.ore_mineral")},new int[]{1}));
        map.put("REDMUD",blend("REDMUD",new String[]{"WATER"},new int[]{4},new String[]{item("block.duna_sands")},new int[]{1}));
        map.put("SCUTTERBLOOD",blend("SCUTTERBLOOD",new String[]{"HCL"},new int[]{1},new String[]{item("scuttertail")},new int[]{1}));return map;
    }
    public static ProcessRecipe neonTubes() {
        Map<String,Long> out=new LinkedHashMap<>();out.put(item("circuit@0"),m("GLASS_PANE_ITEM",1)+m("CARBON_WIRE_ITEM",1)+m("POLYMER_ITEM",1)+m("NEON",1));
        return bill("neon_tube_filling",new String[]{"NEON"},new int[]{1},new String[]{"GLASS_PANE_ITEM","CARBON_WIRE_ITEM","POLYMER_ITEM"},new int[]{1,1,1},out,1200,60000,0,20);
    }
    public static ProcessRecipe technetium() {
        long metal=m(item("nugget_technetium"),1),acid=r(metal,"441.08988","98.9062508"),solution=r(acid,"1",".65"),product=r(metal,"163.9117908","98.9062508"),liquor=r(product,"1",".30"),offgas=r(metal,"322.0385","98.9062508");int n=q("NITRIC_ACID",solution);
        Map<String,Long> out=new LinkedHashMap<>();out.put("HTCO4",liquor);out.put("NITRIC_ACID",m("NITRIC_ACID",n)-solution);out.put("FRESH_WATER",metal+solution+m("FRESH_WATER",1)-liquor-offgas);out.put("VENT_NOX",offgas);
        return bill("contained_pertechnetate_oxidation",new String[]{"NITRIC_ACID","FRESH_WATER"},new int[]{n,1},new String[]{item("nugget_technetium")},new int[]{1},out,2400,100000,200000,80);
    }
    public static ProcessRecipe dicyanoacetylene() {
        long ethylene=m("UNSATURATEDS",200),ammonia=m("AMMONIA",200),product=r(ethylene,"76.0562","56.10632");Map<String,Long> out=new LinkedHashMap<>();out.put("DICYANOACETYLENE",product);out.put("HYDROGEN_GAS",ethylene+ammonia-product);
        return bill("endothermic_carbon_nitrogen_condensation",new String[]{"UNSATURATEDS","AMMONIA"},new int[]{200,200},new String[0],new int[0],out,3600,200000,10000000,1200);
    }
    public static ProcessRecipe eggSeparation() {
        long egg=m("EGG",1),ligand=m("RADIOSOLVENT",1),cholesterol=m(item("vanilla.egg"),1)/100;Map<String,Long> out=new LinkedHashMap<>();out.put("CHOLESTEROL",cholesterol);out.put("RADIOSOLVENT",ligand);out.put(item("process_residue"),egg-ligand-cholesterol);
        return bill("biological_ligand_recovery",new String[]{"EGG"},new int[]{1},new String[0],new int[0],out,2400,100000,100000,80);
    }
    public static Map<Integer,ProcessRecipe> defaults() {Map<Integer,ProcessRecipe> map=new LinkedHashMap<>();map.put(1073,neonTubes());map.put(1197,technetium());map.put(1198,dicyanoacetylene());map.put(1199,eggSeparation());return map;}
}
