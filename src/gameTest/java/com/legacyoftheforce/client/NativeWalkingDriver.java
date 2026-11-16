package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Walks native path hints using real player input; never writes position, velocity or movement packets. */
public final class NativeWalkingDriver {
    private static int sequence;
    private static final Map<Integer,List<BlockPos>> RECEIVED=new HashMap<>();
    private final String goal;private final BlockPos target;private List<BlockPos> nodes=List.of();private int cursor,pending,replans;private long requested,lastProgress;private Vec3 previous;
    public NativeWalkingDriver(String goal,BlockPos target) {this.goal=goal;this.target=target;}
    public static void busy(int sequence) {RECEIVED.put(sequence,List.of());}
    public static void receive(String data) {
        String[] parts=data.split(" ",2);int id=Integer.parseInt(parts[0]);var nodes=new ArrayList<BlockPos>();
        for(var triple:parts[1].split(";")) {var n=triple.split(",");nodes.add(new BlockPos(Integer.parseInt(n[0]),Integer.parseInt(n[1]),Integer.parseInt(n[2])));}
        if(nodes.size()>32||RECEIVED.size()>4)throw new IllegalStateException("Bounded native path response required");RECEIVED.put(id,nodes);
    }
    public boolean step(Minecraft mc,long tick) {
        if(mc.screen!=null) {stop(mc);return false;}
        var position=mc.player.position();if(previous==null) {previous=position;lastProgress=tick;}
        if(position.distanceToSqr(previous)>.25) {previous=position;lastProgress=tick;replans=0;}
        if(position.subtract(Vec3.atBottomCenterOf(target)).multiply(1,0,1).length()<.42&&Math.abs(position.y-target.getY())<.7) {stop(mc);return true;}
        if(pending!=0) {
            stop(mc);var response=RECEIVED.remove(pending);if(response!=null) {nodes=response;cursor=0;pending=0;}
            else {if(tick-requested>200)throw new IllegalStateException("No native path response for "+goal);return false;}
        }
        if(tick-lastProgress>100) {if(++replans>4)throw new IllegalStateException("Native walking stuck at "+position+" goal="+goal+" next="+(cursor<nodes.size()?nodes.get(cursor):target));nodes=List.of();cursor=0;lastProgress=tick;}
        while(cursor<nodes.size()) {
            var node=nodes.get(cursor);var delta=position.subtract(Vec3.atBottomCenterOf(node));if(Math.abs(delta.x)<.38&&Math.abs(delta.z)<.38&&Math.abs(delta.y)<.8)cursor++;else break;
        }
        if(cursor==nodes.size()) {
            stop(mc);if(tick-requested<40)return false;pending=++sequence;requested=tick;mc.getConnection().sendCommand("lotf_adventure_journey path "+goal+" "+pending);return false;
        }
        var node=nodes.get(cursor);face(mc,Vec3.atBottomCenterOf(node).add(0,mc.player.getEyeHeight(),0));
        mc.options.keyUp.setDown(true);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keySprint.setDown(false);
        mc.options.keyJump.setDown(mc.player.horizontalCollision||node.getY()>mc.player.getY()+.4||mc.player.isInWater());return false;
    }
    public static void face(Minecraft mc,Vec3 target) {var d=target.subtract(mc.player.getEyePosition());mc.player.setYRot((float)(Math.atan2(d.z,d.x)*180/Math.PI-90));mc.player.setXRot((float)(-Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))*180/Math.PI));}
    public static void stop(Minecraft mc) {mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);mc.options.keyJump.setDown(false);mc.options.keySprint.setDown(false);}
}
