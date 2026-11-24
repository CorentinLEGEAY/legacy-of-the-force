package com.legacyoftheforce.speeder;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Bounded planar approximation, not a rigid-body solver. Cosmetic fields never enter the calculation. */
public final class SpeederHandling {
    public record Settings(double inertiaPenalty,double referenceRadiusSquared,double minimumResponse,double maximumTrim,double stabilizerDamping,double cargoMassPerItem,double passengerMass){
        public Settings {bound(inertiaPenalty,0,1);bound(referenceRadiusSquared,0,4);bound(minimumResponse,.5,1);bound(maximumTrim,0,.35);bound(stabilizerDamping,0,1);bound(cargoMassPerItem,.001,10);bound(passengerMass,0,100);}
        public Settings(double inertiaPenalty,double referenceRadiusSquared,double minimumResponse,double maximumTrim,double stabilizerDamping){this(inertiaPenalty,referenceRadiusSquared,minimumResponse,maximumTrim,stabilizerDamping,.02,2);}
        public static Settings defaults(){return new Settings(.12,.35,.55,.25,.75,.02,2);}
    }
    public record Analysis(double response,double thrustTrim,double radiusSquared,double centerX,double centerZ){
        public Analysis {bound(response,.5,1);bound(thrustTrim,-.35,.35);bound(radiusSquared,0,100);if(!Double.isFinite(centerX)||!Double.isFinite(centerZ))throw new IllegalArgumentException("Invalid mass center");}
    }
    private record Point(double x,double y,double z,double mass,double forward,double side){}
    public static Analysis draft(SpeederBlueprint.Plan plan,Map<ResourceLocation,SpeederModule> catalog,Settings settings){return inspect(plan,Collections.nCopies(plan.modules().size(),100),SpeederCargoData.empty(),catalog,false,0,settings);}
    public static Analysis inspect(VehicleState.Snapshot state,Map<ResourceLocation,SpeederModule> catalog,boolean pilot,int guests,Settings settings){return inspect(state.blueprint().plan().orElseThrow(),state.health(),state.cargo(),catalog,pilot,guests,settings);}
    private static Analysis inspect(SpeederBlueprint.Plan plan,List<Integer> health,SpeederCargoData cargo,Map<ResourceLocation,SpeederModule> catalog,boolean pilot,int guests,Settings settings){
        if(plan.modules().isEmpty()||plan.modules().size()>128||health.size()!=plan.modules().size()||guests<0||guests>3)throw new IllegalArgumentException("Invalid handling layout");
        var points=new ArrayList<Point>();var cargoSeats=new ArrayList<SpeederBlueprint.Cell>();var guestSeats=new ArrayList<SpeederBlueprint.Cell>();SpeederBlueprint.Cell pilotSeat=null;
        double stability=0;
        for(int i=0;i<plan.modules().size();i++){
            var part=plan.modules().get(i);var definition=Objects.requireNonNull(catalog.get(part.kind()));double hp=health.get(i)/100d;bound(hp,0,1);
            double thrust=(definition.family()==SpeederModule.Family.ENGINE||definition.family()==SpeederModule.Family.THRUSTER)?definition.thrust()*hp:0;
            double forward=switch(part.rotation()){case 0->thrust;case 2->-thrust;default->0;};
            double side=switch(part.rotation()){case 1->thrust;case 3->-thrust;default->0;};
            var at=part.at();points.add(new Point(at.x()*.5,at.y()*.5,at.z()*.5,definition.mass(),forward,side));stability+=definition.stability()*hp;
            if(definition.family()==SpeederModule.Family.CARGO)for(int slot=0;slot<definition.cargoSlots()&&cargoSeats.size()<27;slot++)cargoSeats.add(at);
            if(hp>0&&part.kind().toString().equals("legacyoftheforce:seat_pilot"))pilotSeat=at;
            if(hp>0&&part.kind().toString().equals("legacyoftheforce:seat_passenger"))guestSeats.add(at);
        }
        for(var raw:cargo.items()){
            var stack=(CompoundTag)raw;int slot=stack.getByte("Slot")&255;
            double cargoMass=stack.getInt("count")*settings.cargoMassPerItem();
            if(slot<cargoSeats.size())add(points,cargoSeats.get(slot),cargoMass,0);
            else { // A catalog capacity reduction must retain all historical cargo mass, never crash or erase it.
                double cx=plan.modules().stream().mapToDouble(p->p.at().x()*.5).average().orElse(0);
                double cy=plan.modules().stream().mapToDouble(p->p.at().y()*.5).average().orElse(0);
                double cz=plan.modules().stream().mapToDouble(p->p.at().z()*.5).average().orElse(0);
                points.add(new Point(cx,cy,cz,cargoMass,0,0));
            }
        }
        if(pilot){if(pilotSeat==null)throw new IllegalArgumentException("Pilot without working seat");add(points,pilotSeat,settings.passengerMass(),.5);}
        if(guests>guestSeats.size())throw new IllegalArgumentException("Guests without working seats");
        for(int i=0;i<guests;i++)add(points,guestSeats.get(i),settings.passengerMass(),.5);
        double mass=0,x=0,y=0,z=0;
        for(var point:points){mass+=point.mass();x+=point.x()*point.mass();y+=point.y()*point.mass();z+=point.z()*point.mass();}
        if(!Double.isFinite(mass)||mass<=0)throw new IllegalArgumentException("Invalid handling mass");x/=mass;y/=mass;z/=mass;
        double radius=0,torque=0,power=0,lever=.5;
        for(var point:points){double dx=point.x()-x,dy=point.y()-y,dz=point.z()-z;radius+=point.mass()*(dx*dx+dz*dz+.25*dy*dy);torque+=dx*point.forward()+dz*point.side();power+=Math.abs(point.forward())+Math.abs(point.side());lever=Math.max(lever,Math.max(Math.abs(dx),Math.abs(dz)));}
        radius/=mass;
        double response=Math.clamp(1/(1+settings.inertiaPenalty()*Math.max(0,radius-settings.referenceRadiusSquared())),settings.minimumResponse(),1);
        double trim=power>0?Math.clamp(torque/(power*lever),-1,1)*settings.maximumTrim()*(1-settings.stabilizerDamping()*Math.clamp(stability,0,1)):0;
        return new Analysis(response,trim,radius,x,z);
    }
    private static void add(List<Point> points,SpeederBlueprint.Cell at,double mass,double height){if(!Double.isFinite(mass)||mass<0)throw new IllegalArgumentException("Invalid added mass");points.add(new Point(at.x()*.5,at.y()*.5+height,at.z()*.5,mass,0,0));}
    private static void bound(double n,double low,double high){if(!Double.isFinite(n)||n<low||n>high)throw new IllegalArgumentException("Handling value outside bounds");}
    private SpeederHandling(){}
}
