package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/** Compressed organic process identities. Ratios are molecular balances; utilities and selectivity are game assumptions. */
public final class OrganicProcesses {
    private OrganicProcesses() { }
    private static String item(String field) { return SpecialistProcesses.key(field); }
    private static long m(String id,int n) { return ChemistryProcesses.mass(id,n); }
    private static long r(long amount,String numerator,String denominator) { return BigDecimal.valueOf(amount).multiply(new BigDecimal(numerator)).divide(new BigDecimal(denominator),0,RoundingMode.DOWN).longValueExact(); }
    private static int q(String id,long mass) { return Math.toIntExact(PetroleumProcesses.ceilDiv(mass,m(id,1))); }
    private static void add(Map<String,Long> out,String id,long mass) { if(mass>0) out.put(id,out.containsKey(id)?out.get(id)+mass:mass); }
    private static void state(String id,PhysicalState.Phase phase,PhysicalState.Basis basis,String mass,BigDecimal lhv,String reference) {
        PhysicalRegistry.adopt(id,new PhysicalState(id,phase,basis,new BigDecimal(mass).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).setScale(0,RoundingMode.DOWN).longValueExact(),lhv,reference,null));
    }
    private static void solid(String field,String kg,String lhv) { state(item(field),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,kg,new BigDecimal(lhv),"weighed organic process packet; LHV is not detonation work"); }
    static void registerAssays() {
        state("TOLUENE",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,".867",new BigDecimal("40580000"),"20 C reference liquid; adopted density/LHV");
        state("GLYCEROL",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,"1.26",new BigDecimal("16000000"),"20 C reference liquid; adopted density/LHV");
        state("NITROGLYCERIN",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,"1.59",new BigDecimal("6500000"),"20 C reference liquid; adopted combustion energy, not explosive yield");
        state("MUSTARDGAS",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,"1.27",new BigDecimal("18000000"),"contained organic liquid at reference temperature; historical name does not imply standard gas litres");
        state("ROCKET_FUEL",PhysicalState.Phase.LIQUID,PhysicalState.Basis.LIQUID_LITER,"1",new BigDecimal("15005700"),"90 wt% hydrazine / 10 wt% water monopropellant; no premixed oxidizer");
        solid("hexamine","1","30000000");solid("rdx_powder",".1","9000000");solid("nitrocellulose",".25","10000000");solid("ball_tnt",".25","14500000");solid("ingot_c4",".25","10528000");solid("cordite",".25","10460000");solid("ball_tatb",".25","10000000");solid("powder_poison",".1","12000000");
        long dynamite=m("NITROGLYCERIN",1)+m(item("powder_quartz"),1);
        state(item("ball_dynamite"),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,".25",ProcessRecipe.energy("NITROGLYCERIN",m("NITROGLYCERIN",1)).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).divide(BigDecimal.valueOf(dynamite),18,RoundingMode.DOWN),"adsorbent/organic energetic preparation; no fuel multiplier");
        solid("ammo_arty@0","10","0");
        for(int i=0;i<3;i++) {
            String gas=i==0?"CHLORINE":i==1?"PHOSGENE":"MUSTARDGAS";long mass=m(item("ammo_arty@0"),1)+m("POLYMER_ITEM",1)+m(gas,i<2?100:1);
            state(item("ammo_arty@"+(9+i)),PhysicalState.Phase.SOLID,PhysicalState.Basis.MATERIAL_ITEM,BigDecimal.valueOf(mass).divide(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG),18,RoundingMode.DOWN).toPlainString(),
                    ProcessRecipe.energy("POLYMER_ITEM",m("POLYMER_ITEM",1)).add(ProcessRecipe.energy(gas,m(gas,i<2?100:1))).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).divide(BigDecimal.valueOf(mass),18,RoundingMode.DOWN),"sealed payload plus vessel and polymer packaging; no fluid conversion");
        }
        for(String gas:new String[]{"CHLORINE","AMMONIA","NITROGEN_GAS"}) {
            PhysicalState base=PhysicalRegistry.get(gas);PhysicalRegistry.adopt("VENT_"+gas,new PhysicalState(base.substance,base.phase,base.basis,base.massUnits,base.lhvJoulesPerKg,"explicit sub-unit environmental purge",base.mixture));
        }
    }
    private static ProcessRecipe bill(String id,String[] in,int[] qty,String[] solids,int[] count,Map<String,Long> out,int ticks,long work,long heat,int temperature) {
        return SpecialistProcesses.bill(id,in,qty,solids,count,out,ticks,work,heat,temperature);
    }
    public static ProcessRecipe toluene() {
        long feed=m("AROMATICS",1),h=r(feed,"4.03176","120.19158"),product=r(feed,"92.13842","120.19158");int gas=q("HYDROGEN_GAS",h);Map<String,Long> out=new LinkedHashMap<>();add(out,"TOLUENE",product);add(out,"GAS",feed+h-product);add(out,"HYDROGEN_GAS",m("HYDROGEN_GAS",gas)-h);
        return bill("aromatic_hydrodealkylation",new String[]{"AROMATICS","HYDROGEN_GAS"},new int[]{1,gas},new String[0],new int[0],out,2400,100000,3000000,650);
    }
    public static ProcessRecipe glycerol() {
        long sugar=m("SUGAR_ITEM",1),h=r(sugar,"4.03176","180.15588");int gas=q("HYDROGEN_GAS",h);Map<String,Long> out=new LinkedHashMap<>();add(out,"GLYCEROL",sugar+h);add(out,"HYDROGEN_GAS",m("HYDROGEN_GAS",gas)-h);
        return bill("compressed_glucose_hydrogenolysis",new String[]{"HYDROGEN_GAS"},new int[]{gas},new String[]{"SUGAR_ITEM"},new int[]{1},out,2400,100000,1000000,250);
    }
    public static ProcessRecipe hexamine() {
        long formal=m("FORMALDEHYDE",300),total=formal+m("AMMONIA",200),product=r(formal,"140.18628","180.15588");Map<String,Long> out=new LinkedHashMap<>();add(out,item("hexamine"),product);add(out,"FRESH_WATER",total-product);
        return bill("hexamine_condensation",new String[]{"FORMALDEHYDE","AMMONIA"},new int[]{300,200},new String[0],new int[0],out,2400,100000,600000,100);
    }
    /** Nitration plus carrier-water recovery; exact dissolved acid use, rounded feed excess returned. */
    private static ProcessRecipe nitrate(String id,String feed,boolean fluid,String feedMolar,String product,String productMolar,int acidMoles) {
        long amount=m(feed,1),acid=r(amount,BigDecimal.valueOf(acidMoles).multiply(new BigDecimal("63.01284")).toPlainString(),feedMolar),solution=r(acid,"1",".65"),target=r(amount,productMolar,feedMolar);
        int n=q("NITRIC_ACID",solution);Map<String,Long> out=new LinkedHashMap<>();add(out,product,target);add(out,"FRESH_WATER",amount+solution-target);add(out,"NITRIC_ACID",m("NITRIC_ACID",n)-solution);
        return bill(id,fluid?new String[]{feed,"NITRIC_ACID"}:new String[]{"NITRIC_ACID"},fluid?new int[]{1,n}:new int[]{n},fluid?new String[0]:new String[]{feed},fluid?new int[0]:new int[]{1},out,2400,100000,fluid?1000000:6000000,100);
    }
    public static ProcessRecipe tnt() { return nitrate("aromatic_nitration","TOLUENE",true,"92.13842",item("ball_tnt"),"227.1311",3); }
    public static ProcessRecipe nitroglycerin() { return nitrate("glycerol_nitration","GLYCEROL",true,"92.09382","NITROGLYCERIN","227.0865",3); }
    public static ProcessRecipe nitrocellulose() { return nitrate("cellulose_nitration","PAPER_ITEM",false,"162.1406",item("nitrocellulose"),"297.13328",3); }
    public static ProcessRecipe rdx() {
        long feed=m(item("hexamine"),1),acid=r(feed,"252.05136","140.18628"),solution=r(acid,"1",".65"),rdx=r(feed,"222.11634","140.18628"),form=r(feed,"90.07794","140.18628"),salt=feed+acid-rdx-form;int n=q("NITRIC_ACID",solution);
        Map<String,Long> out=new LinkedHashMap<>();add(out,item("rdx_powder"),rdx);add(out,"FORMALDEHYDE",form);add(out,"AMMONIUM_NITRATE_ITEM",salt);add(out,"FRESH_WATER",solution-acid);add(out,"NITRIC_ACID",m("NITRIC_ACID",n)-solution);
        return bill("compressed_hexamine_nitration",new String[]{"NITRIC_ACID"},new int[]{n},new String[]{item("hexamine")},new int[]{1},out,3600,100000,2000000,100);
    }
    public static ProcessRecipe c4() {
        long rdx=m(item("rdx_powder"),10),binder=r(rdx,".04",".96");Map<String,Long> out=new LinkedHashMap<>();add(out,item("ingot_c4"),rdx+binder);add(out,"POLYMER_ITEM",m("POLYMER_ITEM",1)-binder);
        return bill("plasticized_explosive_formulation",new String[0],new int[0],new String[]{item("rdx_powder"),"POLYMER_ITEM"},new int[]{10,1},out,1200,60000,0,20);
    }
    public static ProcessRecipe cordite() {
        long nc=m(item("nitrocellulose"),4),ng=r(nc,".30",".65"),oil=r(nc,".05",".65");Map<String,Long> out=new LinkedHashMap<>();add(out,item("cordite"),nc+ng+oil);add(out,"NITROGLYCERIN",m("NITROGLYCERIN",1)-ng);add(out,"LUBRICANT",m("LUBRICANT",1)-oil);add(out,"ETHANOL",m("ETHANOL",1));
        return bill("propellant_granulation",new String[]{"NITROGLYCERIN","LUBRICANT","ETHANOL"},new int[]{1,1,1},new String[]{item("nitrocellulose")},new int[]{4},out,2400,100000,1000000,100);
    }
    public static ProcessRecipe dynamite() {
        Map<String,Long> out=new LinkedHashMap<>();add(out,item("ball_dynamite"),m("NITROGLYCERIN",1)+m(item("powder_quartz"),1));return bill("adsorbent_formulation",new String[]{"NITROGLYCERIN"},new int[]{1},new String[]{item("powder_quartz")},new int[]{1},out,1200,50000,0,20);
    }
    public static ProcessRecipe poison() {
        long sodium=m("SODIUM_ITEM",1),carbon=r(sodium,"12.0107","22.98977"),nitrogen=r(sodium,"14.0067","22.98977");int gas=q("NITROGEN_GAS",nitrogen);Map<String,Long> out=new LinkedHashMap<>();add(out,item("powder_poison"),sodium+carbon+nitrogen);add(out,"CHAR_ITEM",m("CHAR_ITEM",1)-carbon);add(out,"VENT_NITROGEN_GAS",m("NITROGEN_GAS",gas)-nitrogen);
        return bill("alkali_carbon_nitrogen_balance",new String[]{"NITROGEN_GAS"},new int[]{gas},new String[]{"SODIUM_ITEM","CHAR_ITEM"},new int[]{1,1},out,3600,100000,3000000,700);
    }
    public static ProcessRecipe blisterCompound() {
        long sulfur=m("SULFUR_ITEM",1),ethylene=r(sulfur,"56.10632","32.065"),chlorine=r(sulfur,"70.906","32.065");int e=q("UNSATURATEDS",ethylene),c=q("CHLORINE",chlorine);Map<String,Long> out=new LinkedHashMap<>();add(out,"MUSTARDGAS",sulfur+ethylene+chlorine);add(out,"VENT_UNSATURATEDS",m("UNSATURATEDS",e)-ethylene);add(out,"VENT_CHLORINE",m("CHLORINE",c)-chlorine);
        return bill("compressed_organic_sulfur_chlorine_balance",new String[]{"UNSATURATEDS","CHLORINE"},new int[]{e,c},new String[]{"SULFUR_ITEM"},new int[]{1},out,6000,100000,0,20);
    }
    public static ProcessRecipe tatb() {
        long benzene=m("BENZENE",1),cl=r(benzene,"212.718","78.11184"),ammonia=r(benzene,"102.18312","78.11184"),acid=r(benzene,"189.03852","78.11184"),solution=r(acid,"1",".65"),product=r(benzene,"258.14844","78.11184"),salt=r(benzene,"160.47438","78.11184"),hcl=r(benzene,"109.38282","78.11184"),liquor=r(hcl,"1",".30");
        int c=q("CHLORINE",cl),a=q("AMMONIA",ammonia),n=q("NITRIC_ACID",solution);Map<String,Long> out=new LinkedHashMap<>();add(out,item("ball_tatb"),product);add(out,"AMMONIUM_CHLORIDE_ITEM",salt);add(out,"HCL",liquor);add(out,"FRESH_WATER",benzene+cl+ammonia+solution+m("ICE_BLOCK",3)-product-salt-liquor);add(out,"NITRIC_ACID",m("NITRIC_ACID",n)-solution);add(out,"VENT_CHLORINE",m("CHLORINE",c)-cl);add(out,"VENT_AMMONIA",m("AMMONIA",a)-ammonia);
        return bill("compressed_substituted_aromatic_balance",new String[]{"BENZENE","CHLORINE","AMMONIA","NITRIC_ACID"},new int[]{1,c,a,n},new String[]{"ICE_BLOCK"},new int[]{3},out,12000,300000,3000000,200);
    }
    public static ProcessRecipe payload(int kind) {
        String fluid=kind==0?"CHLORINE":kind==1?"PHOSGENE":"MUSTARDGAS";int charge=kind<2?100:1;Map<String,Long> out=new LinkedHashMap<>();add(out,item("ammo_arty@"+(9+kind)),m(item("ammo_arty@0"),1)+m("POLYMER_ITEM",1)+m(fluid,charge));
        return bill("sealed_payload_"+kind,new String[]{fluid},new int[]{charge},new String[]{item("ammo_arty@0"),"POLYMER_ITEM"},new int[]{1,1},out,1200,250000,0,20);
    }
    public static ProcessRecipe rocketFuel() {
        Map<String,Long> out=new LinkedHashMap<>();add(out,"ROCKET_FUEL",m("HYDRAZINE",9)+m("FRESH_WATER",1));return bill("inhibited_monopropellant_blend",new String[]{"HYDRAZINE","FRESH_WATER"},new int[]{9,1},new String[0],new int[0],out,2400,60000,0,20);
    }
    public static Map<Integer,ProcessRecipe> defaults() {
        Map<Integer,ProcessRecipe> map=new LinkedHashMap<>();map.put(83,tnt());map.put(84,c4());map.put(53,cordite());map.put(57,rocketFuel());map.put(89,dynamite());map.put(95,tatb());map.put(123,poison());map.put(115,payload(0));map.put(116,payload(1));map.put(117,payload(2));map.put(1190,toluene());map.put(1191,glycerol());map.put(1192,hexamine());map.put(1193,rdx());map.put(1194,nitrocellulose());map.put(1195,nitroglycerin());map.put(1196,blisterCompound());return map;
    }
}
