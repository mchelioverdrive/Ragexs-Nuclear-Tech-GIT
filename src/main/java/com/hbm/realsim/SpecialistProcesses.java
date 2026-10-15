package com.hbm.realsim;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** Explicit specialist assays and process bills; the ITEM namespace binds existing native forms. */
public final class SpecialistProcesses {
    private SpecialistProcesses() { }
    private static final Map<String, Item> ITEMS = new java.util.HashMap<>();
    public static String key(String field) {
        if(field.equals("powder_aluminium")) return "ALUMINIUM_ITEM";
        if(field.equals("powder_boron")) return "BORON_ITEM";
        if(field.equals("powder_calcium")) return "CALCIUM_ITEM";
        if(field.equals("lithium")) return "LITHIUM_ITEM";
        if(field.equals("potassium_powder")) return "POTASSIUM_ITEM";
        if(field.equals("powder_caesium")) return "CESIUM_ITEM";
        if(field.equals("ingot_barium")) return "BARIUM_ITEM";
        
        return "ITEM:" + field;
    }
    public static ItemStack stack(String name, int count) {
        if(!name.startsWith("ITEM:")) return null;
        String field = name.substring(5); int meta=0; int at=field.indexOf('@'); if(at>=0){meta=Integer.parseInt(field.substring(at+1));field=field.substring(0,at);}
        String cacheKey=field; Item item = ITEMS.get(cacheKey);
        if(item == null) try {
            Class<?> owner = com.hbm.items.ModItems.class;
            if(field.startsWith("block.")) { owner = com.hbm.blocks.ModBlocks.class; field = field.substring(6); }
            else if(field.startsWith("vanilla.")) { owner = net.minecraft.init.Items.class; field = field.substring(8); }
            Object value = owner.getField(field).get(null);
            item = value instanceof net.minecraft.block.Block ? Item.getItemFromBlock((net.minecraft.block.Block) value) : (Item) value;
            if(item != null) ITEMS.put(cacheKey, item);
        } catch(ReflectiveOperationException missing) { throw new IllegalArgumentException("Missing native process form: " + name, missing); }
        if(item == null) throw new IllegalStateException("Process forms are not initialized: " + name);
        return new ItemStack(item, count, meta);
    }
    static void solid(String field, String kg) {
        String id = key(field); if(!id.startsWith("ITEM:")) return;
        long mass = new BigDecimal(kg).multiply(BigDecimal.valueOf(PhysicalState.MASS_UNITS_PER_KG)).setScale(0, RoundingMode.DOWN).longValueExact();
        PhysicalRegistry.adopt(id, new PhysicalState(field, PhysicalState.Phase.SOLID, PhysicalState.Basis.MATERIAL_ITEM, mass,
                BigDecimal.ZERO, "adopted specialist process packet; not a universal item/ingot rule", null));
    }
    private static void gas(String id, String molarMass) {
        PhysicalRegistry.adopt(id, new PhysicalState(id, PhysicalState.Phase.GAS, PhysicalState.Basis.STANDARD_GAS_LITER,
                new BigDecimal(molarMass).multiply(new BigDecimal("1000000000")).longValueExact(), BigDecimal.ZERO, "0 C, 1 bar reference amount", null));
    }
    static void registerAssays() {
        for(String field : new String[] {"powder_terbium2", "powder_terbium_oxide", "powder_terbium_fluoride", "powder_ytterbium_oxide", "powder_ytterbium_fluoride",
                "fragment_samarium", "powder_samarium", "europiumsol", "terbiumsol", "dysprosiumsol", "lutetiumsol", "thuliumsol",
                "powder_dysprosium2", "powder_lutetium2", "powder_thulium2", "powder_yttrium", "erbium_powder", "powder_ytterbium", "powder_cerium",
                "gem_sapphire", "block.glass_quartz", "ingot_phosphorus", "powder_zircon", "ingot_hafnium", "block.ore_beryllium", "block.ore_magnesite", "calcined_dolomite",
                "beryllium_oxide", "beryllium_fluoride", "magnesium_fluoride", "magnesium_ingot", "powder_beryllium", "powder_quartz", "powder_sodium_silicate",
                "ingot_praseodymium", "ingot_dysprosium", "ingot_europium", "ingot_terbium", "powder_uranium", "powder_spent_haleu", "powder_spent_leu", "powder_spent_thorium", "powder_spent_u233", "powder_spent_mox", "powder_du_spent",
                "powder_thorium", "nuclear_waste", "radium_residue", "process_residue", "powder_zirconium", "zirconium_oxide", "hafnium_oxide", "ingot_titanium", "powder_chromium", "ingot_chromium", "ingot_vanadium"}) solid(field, "1");
        for(String field : new String[] {"KTP_crystal", "BBO_crystal", "titanium_sapphire_crystal", "lithium_niobate_crystal", "visible_dopant_crystal", "cerium_crystal", "KDP_crystal", "CLBO_crystal", "LBO_crystal", "er_yag_feedstock", "yby_feedstock", "calcium_fluoride_crystal"}) solid(field, ".1");
        for(String field : new String[] {"europium_dust_tiny", "powder_erbium_tiny", "powder_ytterbium_tiny", "powder_yttrium_tiny", "powder_neodymium_tiny", "nugget_th232", "nugget_uranium", "nugget_neptunium", "nugget_u233", "nugget_americium_fuel", "nugget_hafnium", "barium_nugget", "nugget_niobium"}) {
            String id = key(field);
            PhysicalRegistry.adopt(id, new PhysicalState(field, PhysicalState.Phase.SOLID, PhysicalState.Basis.MATERIAL_ITEM, PhysicalState.MASS_UNITS_PER_KG / 9,
                    BigDecimal.ZERO, "one ninth kg specialist nugget/dust packet", null));
        }
        solid("regolith_feed", "100");
        solid("nugget_ra226", ".001");
        PhysicalRegistry.adopt("WASTEFLUID", new PhysicalState("radioactive_process_residue", PhysicalState.Phase.LIQUID, PhysicalState.Basis.NOMINAL,
                PhysicalState.MASS_UNITS_PER_KG / 1000, BigDecimal.ZERO, "one gram waste-equivalent process parcel; not a liquid litre", null));
        gas("WASTEGAS", "124.16875");
        gas("HELIUM3", "3.0160293"); gas("TRITIUM", "6.03209856");
        PhysicalRegistry.adopt("RADIOSOLVENT", new PhysicalState("recycled_chelating_working_liquor", PhysicalState.Phase.LIQUID, PhysicalState.Basis.LIQUID_LITER,
                PhysicalState.MASS_UNITS_PER_KG, BigDecimal.ZERO, "fictional working ligand; 1 kg/reference L, no certified fuel or heat value", null));
    }
    private static long m(String name, int count) { return ChemistryProcesses.mass(name, count); }
    private static long r(long mass, String a, String b) { return BigDecimal.valueOf(mass).multiply(new BigDecimal(a)).divide(new BigDecimal(b), 0, RoundingMode.DOWN).longValueExact(); }
    private static int q(String name, long mass) { return Math.toIntExact(PetroleumProcesses.ceilDiv(mass, m(name, 1))); }
    private static void add(Map<String, Long> map, String key, long mass) { if(mass > 0) map.put(key, map.containsKey(key) ? Math.addExact(map.get(key), mass) : mass); }
    static ProcessRecipe bill(String id, String[] in, int[] quantities, String[] solids, int[] counts, Map<String, Long> out, int ticks, long work, long heat, int temperature) {
        long total = 0; BigDecimal potential = BigDecimal.ZERO;
        for(int i=0;i<in.length;i++) { long mass=m(in[i],quantities[i]); total+=mass; potential=potential.add(ProcessRecipe.energy(in[i],mass)).add(ProcessRecipe.sensible(in[i],mass)); }
        for(int i=0;i<solids.length;i++) { long mass=m(solids[i],counts[i]); total+=mass; potential=potential.add(ProcessRecipe.energy(solids[i],mass)).add(ProcessRecipe.sensible(solids[i],mass)); }
        long product = 0; for(Map.Entry<String,Long> e:out.entrySet()) { product+=e.getValue(); potential=potential.subtract(ProcessRecipe.energy(e.getKey(),e.getValue())).subtract(ProcessRecipe.sensible(e.getKey(),e.getValue())); }
        if(product!=total) throw new IllegalArgumentException(id+" unassigned mass: "+(total-product));
        ticks = Math.max(ticks, Math.toIntExact(Math.max(PetroleumProcesses.ceilDiv(work*20,8000),PetroleumProcesses.ceilDiv(heat*20,100000))));
        long rejection=potential.add(BigDecimal.valueOf(work+heat)).max(BigDecimal.ZERO).setScale(0,RoundingMode.CEILING).longValueExact();
        ticks=Math.max(ticks,Math.toIntExact(PetroleumProcesses.ceilDiv(rejection*20,200000)));
        long[] values=new long[out.size()]; int j=0; for(long value:out.values()) values[j++]=value;
        return new ProcessRecipe("specialist:"+id,in,quantities,new int[in.length],solids,counts,out.keySet().toArray(new String[out.size()]),values,ticks,work,heat,temperature,false);
    }
    public static ProcessRecipe fluoride(int kind) {
        String feed=kind==0?key("powder_terbium_oxide"):kind==1?key("powder_ytterbium_oxide"):"CALCIUM_ITEM";
        long mass=m(feed,1), pure=r(mass,kind==0?"240.0761184":kind==1?"120.0380592":"40.0126864",kind==0?"747.6972":kind==1?"394.1062":"40.078");
        long solution=r(pure,"1",".48"); int amount=q("HYDROFLUORIC_ACID",solution);
        long product=r(mass,kind==0?"863.6822384":kind==1?"460.0984192":"78.0748064",kind==0?"747.6972":kind==1?"394.1062":"40.078");
        long oxygen=kind==0?r(mass,"15.9994","747.6972"):0;
        Map<String,Long> out=new LinkedHashMap<>(); add(out,key(kind==0?"powder_terbium_fluoride":kind==1?"powder_ytterbium_fluoride":"calcium_fluoride_crystal"),product);
        add(out,"FRESH_WATER",kind==2 ? solution-pure : solution+mass-product-oxygen); add(out,"OXYGEN_GAS",oxygen);
        if(kind==2) add(out,"HYDROGEN_GAS",mass+pure-product);
        add(out,"HYDROFLUORIC_ACID",m("HYDROFLUORIC_ACID",amount)-solution);
        return bill("fluoride_"+kind,new String[]{"HYDROFLUORIC_ACID"},new int[]{amount},new String[]{feed},new int[]{1},out,2400,50000,2500000,350);
    }
    public static ProcessRecipe terbiumCalcination() {
        long feed=m(key("powder_terbium2"),1), oxide=r(feed,".7","1"); Map<String,Long> out=new LinkedHashMap<>();
        add(out,key("powder_terbium_oxide"),oxide); add(out,"VENT_STEAM",feed-oxide);
        return bill("hydrated_terbium_calcination",new String[0],new int[0],new String[]{key("powder_terbium2")},new int[]{1},out,2400,20000,1500000,500);
    }
    public static ProcessRecipe rareEarthSeparation(int kind) {
        String[] feeds={"terbiumsol","dysprosiumsol","lutetiumsol","thuliumsol"}; String[] products={"powder_terbium2","powder_dysprosium2","powder_lutetium2","powder_thulium2"};
        long total=m(key(feeds[kind]),1); Map<String,Long> out=new LinkedHashMap<>(); long a=total/100,b=total/200,c=total/200;
        if(kind==0) { add(out,key("powder_yttrium_tiny"),a);add(out,key("nugget_th232"),b);add(out,key("nugget_uranium"),c); }
        if(kind==1) { add(out,key("powder_yttrium_tiny"),a);add(out,key("powder_neodymium_tiny"),b);add(out,"REFINED_GADOLINIUM_ITEM",c); }
        if(kind>=2) { add(out,key("powder_ytterbium_tiny"),a);add(out,key("powder_erbium_tiny"),b+c); }
        add(out,key(products[kind]),total-a-b-c);add(out,"RADIOSOLVENT",m("RADIOSOLVENT",1));
        return bill("rare_earth_recovery_"+kind,new String[]{"RADIOSOLVENT"},new int[]{1},new String[]{key(feeds[kind])},new int[]{1},out,2400,600000,0,20);
    }
    public static ProcessRecipe europiumSulfate() {
        long eu=m(key("europium_dust_tiny"),9), pure=r(eu,"294.23544","303.928"), solution=r(pure,"1",".98"); int amount=q("SULFURIC_ACID",solution);
        long hydrogen=r(eu,"6.04764","303.928"); Map<String,Long> out=new LinkedHashMap<>();
        add(out,key("europiumsol"),eu+pure-hydrogen);add(out,"HYDROGEN_GAS",hydrogen);add(out,"FRESH_WATER",solution-pure);add(out,"SULFURIC_ACID",m("SULFURIC_ACID",amount)-solution);
        return bill("europium_sulfate",new String[]{"SULFURIC_ACID"},new int[]{amount},new String[]{key("europium_dust_tiny")},new int[]{9},out,2400,20000,3000000,120);
    }
    /** Oxygen, all unreacted precursors and the water carrier are explicit, including optical dopants. */
    private static ProcessRecipe oxideCrystal(String product,String[] fields,long[] used,long oxygen,int temperature,long heat) {
        String[] solids=new String[fields.length];int[] counts=new int[fields.length];long total=oxygen;Map<String,Long> out=new LinkedHashMap<>();
        for(int i=0;i<fields.length;i++){solids[i]=key(fields[i]);counts[i]=1;total+=used[i];}
        add(out,key(product),total);
        for(int i=0;i<fields.length;i++)add(out,solids[i],m(solids[i],1)-used[i]);
        int amount=q("OXYGEN_GAS",oxygen);add(out,"OXYGEN_GAS",m("OXYGEN_GAS",amount)-oxygen);
        return bill(product,new String[]{"OXYGEN_GAS"},new int[]{amount},solids,counts,out,2400,200000,heat,temperature);
    }
    public static ProcessRecipe ktp() {
        long ti=m(key("ingot_titanium"),1)/9,k=r(ti,"39.0983","47.867"),p=r(ti,"30.973762","47.867"),o=r(ti,"79.997","47.867");
        return oxideCrystal("KTP_crystal",new String[]{"ingot_titanium","potassium_powder","ingot_phosphorus"},new long[]{ti,k,p},o,1000,2000000);
    }
    public static ProcessRecipe borate(int kind) {
        String base=kind==0?"barium_nugget":kind==1?"lithium":"powder_caesium"; long metal=kind==0?m(key(base),1):m(key(base),1)/100;
        long boron=r(metal,kind==0?"21.622":kind==1?"32.433":"64.866",kind==0?"137.327":kind==1?"6.941":"132.9054519");
        long oxygen=r(metal,kind==0?"63.9976":kind==1?"79.997":"159.994",kind==0?"137.327":kind==1?"6.941":"132.9054519");
        if(kind<2)return oxideCrystal(kind==0?"BBO_crystal":"LBO_crystal",new String[]{base,"powder_boron"},new long[]{metal,boron},oxygen,950,2000000);
        long lithium=r(metal,"6.941","132.9054519");
        return oxideCrystal("CLBO_crystal",new String[]{base,"lithium","powder_boron"},new long[]{metal,lithium,boron},oxygen,950,2000000);
    }
    public static ProcessRecipe lithiumNiobate(){long nb=m(key("nugget_niobium"),1);return oxideCrystal("lithium_niobate_crystal",new String[]{"nugget_niobium","lithium"},new long[]{nb,r(nb,"6.941","92.90638")},r(nb,"47.9982","92.90638"),1300,2500000);}
    public static ProcessRecipe sapphire(){long host=m(key("gem_sapphire"),1),ti=m(key("ingot_titanium"),1)/900,o=r(ti,"31.9988","47.867");return oxideCrystal("titanium_sapphire_crystal",new String[]{"gem_sapphire","ingot_titanium"},new long[]{host,ti},o,1600,2500000);}
    public static ProcessRecipe yag(boolean ytterbium){long y=m(key("powder_yttrium"),1)/10,dop=r(y,ytterbium?".86527":".836295","88.90585"),al=r(y,"134.90769","266.71755")+r(dop,"134.90769",ytterbium?"519.162":"501.777");long o=r(y,"63.9976","88.90585")+r(dop,"63.9976",ytterbium?"173.054":"167.259");return oxideCrystal(ytterbium?"yby_feedstock":"er_yag_feedstock",new String[]{"powder_yttrium","powder_aluminium",ytterbium?"powder_ytterbium_tiny":"powder_erbium_tiny"},new long[]{y,al,dop},o,1600,3000000);}
    public static ProcessRecipe ceriumGlass(){long ce=m(key("powder_cerium"),1)/100;return oxideCrystal("cerium_crystal",new String[]{"block.glass_quartz","powder_cerium"},new long[]{m(key("block.glass_quartz"),1),ce},r(ce,"31.9988","140.116"),1400,3000000);}

    public static ProcessRecipe samariumWash(){long feed=m(key("fragment_samarium"),1);Map<String,Long> out=new LinkedHashMap<>();add(out,key("powder_samarium"),r(feed,".98","1"));add(out,key("process_residue"),feed-r(feed,".98","1"));add(out,"HCL",m("HCL",1));return bill("samarium_surface_cleaning",new String[]{"HCL"},new int[]{1},new String[]{key("fragment_samarium")},new int[]{1},out,1200,100000,0,20);}
    public static ProcessRecipe berylActivation(){long feed=m(key("block.ore_beryllium"),1),be=r(feed,"75.034746","537.501822"),al=r(feed,"101.961276","537.501822");Map<String,Long> out=new LinkedHashMap<>();add(out,key("beryllium_oxide"),be);add(out,"ALUMINA_ITEM",al);add(out,key("powder_quartz"),feed-be-al);add(out,"SODIUM_CARBONATE",m("SODIUM_CARBONATE",1));return bill("beryl_alkaline_activation",new String[]{"SODIUM_CARBONATE"},new int[]{1},new String[]{key("block.ore_beryllium")},new int[]{1},out,2400,200000,4000000,1100);}
    public static ProcessRecipe berylliumFluoride(){long feed=m(key("beryllium_oxide"),1),hf=r(feed,"40.0126864","25.011582"),solution=r(hf,"1",".48"),product=r(feed,"47.0089884","25.011582");int amount=q("HYDROFLUORIC_ACID",solution);Map<String,Long> out=new LinkedHashMap<>();add(out,key("beryllium_fluoride"),product);add(out,"FRESH_WATER",feed+solution-product);add(out,"HYDROFLUORIC_ACID",m("HYDROFLUORIC_ACID",amount)-solution);return bill("beryllium_fluoride",new String[]{"HYDROFLUORIC_ACID"},new int[]{amount},new String[]{key("beryllium_oxide")},new int[]{1},out,2400,100000,3000000,350);}
    public static ProcessRecipe berylliumReduction(){long fluoride=m(key("beryllium_fluoride"),1),mg=r(fluoride,"24.305","47.0089884"),metal=r(fluoride,"9.012182","47.0089884");Map<String,Long> out=new LinkedHashMap<>();add(out,key("powder_beryllium"),metal);add(out,key("magnesium_fluoride"),fluoride+mg-metal);add(out,key("magnesium_ingot"),m(key("magnesium_ingot"),1)-mg);add(out,"ARGON",m("ARGON",10));return bill("beryllium_metallothermic_reduction",new String[]{"ARGON"},new int[]{10},new String[]{key("beryllium_fluoride"),key("magnesium_ingot")},new int[]{1,1},out,2400,200000,3000000,1200);}
    public static ProcessRecipe magnesite(){long feed=m(key("block.ore_magnesite"),1),oxide=r(feed,"40.3044","84.3139");Map<String,Long> out=new LinkedHashMap<>();add(out,key("calcined_dolomite"),oxide);add(out,"CARBONDIOXIDE",feed-oxide);return bill("magnesite_calcination",new String[0],new int[0],new String[]{key("block.ore_magnesite")},new int[]{1},out,2400,20000,2500000,900);}
    public static ProcessRecipe zircon(){long feed=m(key("powder_zircon"),1),zr=r(r(feed,".99","1"),"123.2228","183.3071"),hf=r(r(feed,".01","1"),"210.4888","270.5731");Map<String,Long> out=new LinkedHashMap<>();add(out,key("zirconium_oxide"),zr);add(out,key("hafnium_oxide"),hf);add(out,key("powder_quartz"),feed-zr-hf);add(out,"HYDROFLUORIC_ACID",m("HYDROFLUORIC_ACID",1));return bill("zircon_closed_fluoride_activation",new String[]{"HYDROFLUORIC_ACID"},new int[]{1},new String[]{key("powder_zircon")},new int[]{1},out,2400,200000,5000000,600);}
    public static ProcessRecipe zirconiumReduction(boolean hafnium){String source=key(hafnium?"hafnium_oxide":"zirconium_oxide");long feed=m(source,1),metal=r(feed,hafnium?"178.49":"91.224",hafnium?"210.4888":"123.2228"),calcium=r(feed,"80.156",hafnium?"210.4888":"123.2228");Map<String,Long> out=new LinkedHashMap<>();add(out,key(hafnium?"ingot_hafnium":"powder_zirconium"),metal);add(out,"QUICKLIME_ITEM",feed+calcium-metal);add(out,"CALCIUM_ITEM",m("CALCIUM_ITEM",1)-calcium);add(out,"ARGON",m("ARGON",10));return bill(hafnium?"hafnium_reduction":"zirconium_reduction",new String[]{"ARGON"},new int[]{10},new String[]{source,"CALCIUM_ITEM"},new int[]{1,1},out,2400,200000,3000000,1200);}
    public static ProcessRecipe lunarHelium(){long feed=m(key("regolith_feed"),1),helium=feed/1000000;Map<String,Long> out=new LinkedHashMap<>();add(out,"HELIUM3",helium);add(out,key("process_residue"),feed-helium);return bill("selected_regolith_desorption",new String[0],new int[0],new String[]{key("regolith_feed")},new int[]{1},out,12000,200000,54000000,700).continuous();}
    /** Game fuel powder packets are heavy-metal-equivalent assays, not literal oxide-plus-cladding masses. */
    public static ProcessRecipe reprocess(int kind){String[] feeds={"powder_spent_haleu","powder_spent_leu","powder_spent_thorium","powder_spent_u233","powder_spent_mox","powder_du_spent"};long feed=m(key(feeds[kind]),1);Map<String,Long> out=new LinkedHashMap<>();long main,trace,pu,np;
        if(kind==2){main=r(feed,".94","1");trace=r(feed,".005","1");pu=0;np=0;add(out,key("powder_thorium"),main);add(out,key("nugget_u233"),trace);}
        else if(kind==3){main=r(feed,".9","1");trace=0;pu=0;np=r(feed,".005","1");add(out,key("nugget_u233"),main);}
        else if(kind==4){main=r(feed,".9","1");trace=r(feed,".01","1");pu=0;np=r(feed,".005","1");add(out,"PU_MIX_NUGGET_ITEM",main);add(out,key("nugget_americium_fuel"),trace);}
        else{main=r(feed,kind==5?".97":".9","1");trace=r(feed,kind==0?".02":kind==1?".003":"0","1");pu=r(feed,kind==5?".002":".01","1");np=r(feed,".001","1");add(out,"U238_NUGGET_ITEM",main);add(out,"U235_NUGGET_ITEM",trace);add(out,"PU_MIX_NUGGET_ITEM",pu);}
        add(out,key("nugget_neptunium"),np);
        long gas=kind==5?0:r(feed,".01","1"),xe=r(gas,".8","1");add(out,"XENON",xe);add(out,"KRYPTON",gas-xe);add(out,"WASTEFLUID",feed-main-trace-pu-np-gas);add(out,"HYDROFLUORIC_ACID",m("HYDROFLUORIC_ACID",1));
        return bill("spent_packet_recovery_"+kind,new String[]{"HYDROFLUORIC_ACID"},new int[]{1},new String[]{key(feeds[kind])},new int[]{1},out,3600,500000,5000000,350);
    }
    public static ProcessRecipe kdp(){long k=m("POTASSIUM_ITEM",1)/100,p=r(k,"30.973762","39.0983"),water=r(k,"18.01528","39.0983"),oxygen=r(k,"47.9982","39.0983");int gas=q("OXYGEN_GAS",oxygen);Map<String,Long> out=new LinkedHashMap<>();add(out,key("KDP_crystal"),k+p+water+oxygen);add(out,"POTASSIUM_ITEM",m("POTASSIUM_ITEM",1)-k);add(out,key("ingot_phosphorus"),m(key("ingot_phosphorus"),1)-p);add(out,"FRESH_WATER",m("FRESH_WATER",1)-water);add(out,"OXYGEN_GAS",m("OXYGEN_GAS",gas)-oxygen);return bill("aqueous_kdp",new String[]{"FRESH_WATER","OXYGEN_GAS"},new int[]{1,gas},new String[]{"POTASSIUM_ITEM",key("ingot_phosphorus")},new int[]{1,1},out,2400,100000,1500000,120);}
    public static ProcessRecipe visibleOxides(){String[] fields={"ingot_praseodymium","ingot_dysprosium","ingot_europium","ingot_terbium"};String[] mw={"140.90765","162.5","151.964","158.92535"};long[] used=new long[4];long oxygen=0;for(int i=0;i<4;i++){used[i]=m(key(fields[i]),1);oxygen+=r(used[i],"23.9991",mw[i]);}return oxideCrystal("visible_dopant_crystal",fields,used,oxygen,1200,5000000);}

    public static Map<Integer,ProcessRecipe> defaults(){Map<Integer,ProcessRecipe> map=new LinkedHashMap<>();
        map.put(73,lunarHelium());map.put(1023,berylActivation());map.put(1026,magnesite());map.put(1074,samariumWash());map.put(1097,visibleOxides());map.put(1103,kdp());map.put(1107,zircon());
        for(int i=0;i<5;i++)map.put(1108+i,reprocess(i));map.put(1116,reprocess(5));
        map.put(1182,berylliumFluoride());map.put(1183,berylliumReduction());map.put(1184,zirconiumReduction(false));map.put(1185,zirconiumReduction(true));map.put(1076,europiumSulfate());map.put(1079,rareEarthSeparation(0));map.put(1080,terbiumCalcination());map.put(1081,fluoride(0));map.put(1082,rareEarthSeparation(1));map.put(1083,fluoride(1));map.put(1084,rareEarthSeparation(2));map.put(1085,rareEarthSeparation(3));map.put(1087,yag(false));map.put(1088,yag(true));map.put(1092,ktp());map.put(1093,borate(0));map.put(1094,sapphire());map.put(1095,lithiumNiobate());map.put(1098,ceriumGlass());map.put(1102,fluoride(2));map.put(1104,borate(2));map.put(1105,borate(1));return map;}
}
