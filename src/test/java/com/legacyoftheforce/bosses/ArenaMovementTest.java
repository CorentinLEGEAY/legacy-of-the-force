package com.legacyoftheforce.bosses;

import java.util.Random;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArenaMovementTest {
    private final AABB arena=new AABB(-4,-1,-4,5,5,5);
    @Test void everyDirectionKeepsTheWholeBodyInsideWithoutAddingMotion() {
        var random=new Random(20261116);
        for(int i=0;i<500;i++) {
            double x=-4+random.nextDouble()*8.3,z=-4+random.nextDouble()*8.3,y=-1+random.nextDouble()*3.8;
            var body=new AABB(x,y,z,x+.7,y+2.2,z+.7);var requested=new Vec3(random.nextDouble()*200-100,random.nextDouble()*200-100,random.nextDouble()*200-100);
            var allowed=ArenaMovement.constrain(arena,body,requested);var after=body.move(allowed);
            assertTrue(after.minX>=arena.minX-1e-9 && after.maxX<=arena.maxX+1e-9 && after.minY>=arena.minY-1e-9 && after.maxY<=arena.maxY+1e-9 && after.minZ>=arena.minZ-1e-9 && after.maxZ<=arena.maxZ+1e-9);
            assertTrue(Math.abs(allowed.x)<=Math.abs(requested.x)+1e-9 && Math.abs(allowed.y)<=Math.abs(requested.y)+1e-9 && Math.abs(allowed.z)<=Math.abs(requested.z)+1e-9);
            assertTrue(allowed.x*requested.x>=0 && allowed.y*requested.y>=0 && allowed.z*requested.z>=0);
        }
    }
    @Test void outsidePoseIsNotTeleportedAndInvalidVelocityIsRejected() {
        var outside=new AABB(10,0,0,10.7,2.2,.7);
        assertEquals(Vec3.ZERO,ArenaMovement.constrain(arena,outside,new Vec3(2,0,0)));
        assertEquals(new Vec3(-1,0,0),ArenaMovement.constrain(arena,outside,new Vec3(-1,0,0)));
        assertEquals(Vec3.ZERO,ArenaMovement.constrain(arena,outside,new Vec3(Double.NaN,0,0)));
        assertEquals(Vec3.ZERO,ArenaMovement.constrain(arena,outside,new Vec3(0,Double.POSITIVE_INFINITY,0)));
    }
}
