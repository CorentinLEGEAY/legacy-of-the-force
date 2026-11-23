package com.legacyoftheforce.client;

import com.legacyoftheforce.LegacyOfTheForce;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.MemoryStack;

/** Explicit developer launch preference; absent from normal installed launch arguments. */
final class DevelopmentWindowPlacement {
    private boolean attempted;

    void tick(ClientTickEvent.Post event) {
        if (attempted) return;
        attempted = true;
        String requested = System.getProperty("lotf.dev.display", "");
        if (requested.isBlank()) return;
        if (!requested.matches("[1-9][0-9]?")) {
            LegacyOfTheForce.LOGGER.warn("Invalid developer display number: {}", requested);
            return;
        }
        if (!System.getProperty("os.name", "").startsWith("Windows")) return;
        var monitors = GLFW.glfwGetMonitors();
        if (monitors == null) return;
        String expected = "\\\\.\\DISPLAY" + requested;
        for (int i = 0; i < monitors.remaining(); i++) {
            long monitor = monitors.get(i);
            if (!expected.equalsIgnoreCase(GLFWNativeWin32.glfwGetWin32Adapter(monitor))) continue;
            var window = Minecraft.getInstance().getWindow();
            boolean fullscreen = window.isFullscreen();
            if (fullscreen) window.toggleFullScreen();
            try (var memory = MemoryStack.stackPush()) {
                var x = memory.ints(0); var y = memory.ints(0);
                var width = memory.ints(0); var height = memory.ints(0);
                var actualX = memory.ints(0); var actualY = memory.ints(0);
                GLFW.glfwGetMonitorWorkarea(monitor, x, y, width, height);
                int left = x.get(0) + Math.max(0, (width.get(0) - window.getWidth()) / 2);
                int top = y.get(0) + Math.max(0, (height.get(0) - window.getHeight()) / 2);
                GLFW.glfwSetWindowPos(window.getWindow(), left, top);
                GLFW.glfwGetWindowPos(window.getWindow(), actualX, actualY);
                LegacyOfTheForce.LOGGER.info("Development window display={} monitor={} position={},{} workArea={},{},{},{}", requested,
                        GLFW.glfwGetMonitorName(monitor), actualX.get(0), actualY.get(0), x.get(0), y.get(0), width.get(0), height.get(0));
            }
            if (fullscreen) window.toggleFullScreen();
            return;
        }
        LegacyOfTheForce.LOGGER.warn("Developer display {} unavailable; current window position preserved", requested);
    }
}
