package com.legacyoftheforce.core.operation;

import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.io.IOException;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtomicNbtFileTest {
    @TempDir Path directory;
    private static CompoundTag tag(int value) { var t=new CompoundTag();t.putInt("schemaVersion",1);t.putInt("value",value);return t; }
    @Test @EnabledOnOs(OS.WINDOWS)
    void actualWindowsSharingFailureLeavesTheOldAggregateIntact() throws Exception {
        var target=directory.resolve("state.nbt");AtomicNbtFile.write(target,tag(1),8192);
        try(var held=FileChannel.open(target,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE)) {
            var failure=assertThrows(IOException.class,()->AtomicNbtFile.write(target,tag(2),8192));
            System.out.println("ATOMIC_NATIVE_LOCK_OBSERVED "+failure.getClass().getName()+" reason="+failure.getMessage());
            assertEquals(tag(1),AtomicNbtFile.read(target,8192));
        }
        AtomicNbtFile.write(target,tag(3),8192);assertEquals(tag(3),AtomicNbtFile.read(target,8192));
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void actualNativeLockReleasedAfterTheFirstDeniedMoveRetriesAtomically() throws Exception {
        var target=directory.resolve("state.nbt");var source=directory.resolve("new.nbt");AtomicNbtFile.write(target,tag(1),8192);net.minecraft.nbt.NbtIo.writeCompressed(tag(2),source);
        var held=FileChannel.open(target,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE);int[] pauses={0};
        try {
            AtomicNbtFile.replace(source,target,retry->{ pauses[0]++;assertEquals(tag(1),AtomicNbtFile.read(target,8192));held.close(); });
        } finally { held.close(); }
        assertEquals(1,pauses[0]);assertFalse(Files.exists(source));assertEquals(tag(2),AtomicNbtFile.read(target,8192));
    }
    @Test @EnabledOnOs(OS.WINDOWS)
    void permanentNativeLockRemainsAnErrorAndNeverReplacesTheOldFile() throws Exception {
        var target=directory.resolve("state.nbt");var source=directory.resolve("new.nbt");AtomicNbtFile.write(target,tag(1),8192);net.minecraft.nbt.NbtIo.writeCompressed(tag(2),source);int[] pauses={0};
        try(var held=FileChannel.open(target,StandardOpenOption.READ,ExtendedOpenOption.NOSHARE_DELETE)) {
            assertThrows(AccessDeniedException.class,()->AtomicNbtFile.replace(source,target,retry->pauses[0]++));
            assertEquals(3,pauses[0]);assertEquals(tag(1),AtomicNbtFile.read(target,8192));assertEquals(tag(2),AtomicNbtFile.read(source,8192));
        }
    }
    @Test void unsupportedCrossProviderAtomicMoveNeverFallsBackToCopyOrDelete() throws Exception {
        var source=directory.resolve("source.txt");Files.writeString(source,"new");int[] pauses={0};
        try(var archive=FileSystems.newFileSystem(java.net.URI.create("jar:"+directory.resolve("archive.zip").toUri()),java.util.Map.of("create","true"))) {
            var target=archive.getPath("/state.txt");Files.writeString(target,"old");
            assertThrows(AtomicMoveNotSupportedException.class,()->AtomicNbtFile.replace(source,target,retry->pauses[0]++));
            assertEquals(0,pauses[0]);assertEquals("old",Files.readString(target));assertEquals("new",Files.readString(source));
        }
    }
    @Test void oversizedPayloadCannotAlterTheOldAggregate() throws Exception {
        var target=directory.resolve("state.nbt");AtomicNbtFile.write(target,tag(1),8192);var large=tag(2);large.putString("payload","x".repeat(9000));
        assertThrows(IOException.class,()->AtomicNbtFile.write(target,large,8192));assertEquals(tag(1),AtomicNbtFile.read(target,8192));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
    @Test void readersOnlySeeWholeAggregatesDuringRapidNativeReplacement() throws Exception {
        var target=directory.resolve("state.nbt");
        java.util.function.IntFunction<CompoundTag> image=version->{ var t=tag(version);t.putString("check","version="+version);return t; };
        AtomicNbtFile.write(target,image.apply(0),8192);var running=new java.util.concurrent.atomic.AtomicBoolean(true);var started=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var reader=executor.submit(()-> {
                int reads=0;
                while(running.get()) { var t=AtomicNbtFile.read(target,8192);assertEquals("version="+t.getInt("value"),t.getString("check"));reads++;started.countDown(); }
                return reads;
            });
            try {
                assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));
                for(int version=1;version<=1000;version++) AtomicNbtFile.write(target,image.apply(version),8192);
            } finally { running.set(false); }
            assertTrue(reader.get(10,java.util.concurrent.TimeUnit.SECONDS)>0);
        }
        assertEquals(image.apply(1000),AtomicNbtFile.read(target,8192));
        try(var files=Files.list(directory)) { assertEquals(1,files.count()); }
    }
}
