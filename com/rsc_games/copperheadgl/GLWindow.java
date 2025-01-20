package com.rsc_games.copperheadgl;

import velocity.util.Point;
import velocity.util.Vector2;
import velocity.Driver;
import velocity.config.GlobalAppConfig;
import velocity.system.Images;
import velocity.renderer.DrawTimer;
import velocity.renderer.window.Window;
import velocity.renderer.window.WindowConfig;
import velocity.renderer.window.WindowOption;
import velocity.shader.include.PixelArray;
import velocity.sprite.Camera;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
//import org.lwjgl.opengl.*;
import org.lwjgl.system.*;
import org.lwjgl.glfw.GLFWImage.Buffer;

//import org.joml.Vector2f;

//import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
//import static org.lwjgl.opengl.GL33.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * VXRA's generic window system interface. GLFW / DirectX / AWT or any other frame type
 * may be contained within this class. Actual implementation must be done by each 
 * VXRA-compliant extension renderer.
 */
public class GLWindow implements Window {
    // Internal window reference. Likely an address. Avoid editing at all costs.
    long window;
    DrawTimer dt;
    GLEventHandler eventHandler;
    CopperheadGL copperhead;
    Point windowResolution;

    Point virtualResolution = GlobalAppConfig.bcfg.USE_VIRTUAL_RESOLUTION
        ? GlobalAppConfig.bcfg.APP_VIRTUAL_RES : GlobalAppConfig.bcfg.APP_RES_DEFAULT;
    Point targetVirtualResolution = new Point(virtualResolution.x, virtualResolution.y);

    /**
     * Create a GLFW frame.
     */
    @SuppressWarnings("deprecation")
    public GLWindow(WindowConfig cfg, CopperheadGL rp, Driver m) {
        System.out.println("[copper]: Found LWJGL version " + Version.getVersion());

        // Start initialization.
        String prop = "./lib;./lib/ogl/windows";
        System.setProperty("org.lwjgl.librarypath", prop);
        //System.setProperty("org.lwjgl.util.Debug", "true");
        //System.setProperty("org.lwjgl.util.DebugLoader", "true");
        GLFWErrorCallback.createPrint(System.err).set();

        // Attempt to initialize GLFW. This, if it fails, is a fatal renderer exception
        // and should force VXRA to try the next renderer in the fallback chain.
        if (!glfwInit())
            throw new IllegalStateException("[copper]: fatal failed to init GLFW.");

        // Configure the window based on the passed in config.
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, b2glfw(cfg.getOption(WindowOption.HINT_RESIZABLE)));
        
        // Ensure an OpenGL 3.3 frame context
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);

        // Finally make the window.
        Point frameRes = cfg.getWindowResolution();
        windowResolution = frameRes;
        Camera.res = frameRes;
        String title = cfg.getTitle() + " <Copperhead GL 3.3 Core>";
        this.window = glfwCreateWindow(frameRes.x, frameRes.y, title, NULL, NULL);
        this.copperhead = rp;
        glfwShowWindow(window);

        BufferedImage image = Images.convert(Images.loadRawImage(cfg.getIconPath()), true);
        PixelArray imgWrap = new PixelArray(image); 

        // Set the window icon
        // TODO: Fix icon setting. It doesn't work.
        try (MemoryStack stack = stackPush()) {
            GLFWImage glImg = GLFWImage.create();
            byte[] dataArray = imgWrap.getDataBuffer();
            ByteBuffer dataBuffer = stack.malloc(dataArray.length);
            dataBuffer.put(dataArray);
            dataBuffer.position(0);

            glImg.set(image.getWidth(), image.getHeight(), dataBuffer);

            // And create the image strip to use.
            Buffer images = GLFWImage.malloc(1, stack);
            images.put(glImg);
            images.position(0);
            glfwSetWindowIcon(window, images);
        }
        

        // Finish initializing the window.
        glfwMakeContextCurrent(window);

        if (cfg.getOption(WindowOption.HINT_FULLSCREEN))
            glfwSwapInterval(1);
        else
            glfwSwapInterval(0); // Disable vsync
    }

    /**
     * Convert a boolean to a GLFW constant.
     * 
     * @param value Input boolean value.
     * @return GLFW_TRUE if value == true else GLFW_FALSE
     */
    private int b2glfw(boolean value) {
        return value ? GLFW_TRUE : GLFW_FALSE;
    }

    /**
     * Non-standard renderer method (used only by LVOGL). May be implemented by
     * other renderers if necessary.
     * 
     * @param msPerFrame Milliseconds between each firing of the draw timer.
     * @param iEventHandler Internal frame update event handler.
     */
    public void createFrameTimer(int msPerFrame, GLEventHandler iEventHandler) {
        this.dt = new DrawTimer(msPerFrame, iEventHandler);
    }

    /**
     * Non-standard renderer method (used only by LVOGL). Gets this window's local
     * draw timer.
     * 
     * @return This window's local draw timer.
     */
    public DrawTimer getDrawTimer() {
        return this.dt;
    }

    /**
     * Return this window's current resolution. May change based on previously issued
     * resize events.
     * 
     * @return Window resolution.
     */
    public Point getResolution() {
        Point out = new Point(0, 0);

        // Get frame context location.
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);

            glfwGetWindowSize(window, w, h);
            out.x = w.get(0);
            out.y = h.get(0);
        }
        
        return out;
    }

    @Override
    public Point getVirtualResolution() {
        return this.virtualResolution;
    }

    /**
     * Update the standard and virtual resolutions.
     * 
     * @param width New window width.
     * @param height New window height.
     */
    public void updateResolution(int width, int height) {
        if (GlobalAppConfig.bcfg.USE_VIRTUAL_RESOLUTION) {
            float invAspect = height / (float)width;

            this.virtualResolution = new Point(targetVirtualResolution.x, (int)(targetVirtualResolution.x * invAspect));
        }
        else {
            this.virtualResolution = new Point(width, height);
        }

        this.windowResolution = new Point(width, height);
        Camera.res = new Point(this.virtualResolution.x, this.virtualResolution.y);
        copperhead.iRendererContext.updateResolution(virtualResolution);
    }

    /**
     * Allows critical internal renderer code to access this window's handle.
     * 
     * @return This window handle (or in DirectX + Win32, known as the Hwnd).
     */
    public long getHwnd() {
        return this.window;
    }

    /**
     * Returns this window's current position on screen. May change if the player moves
     * the window.
     * 
     * @return Current window position on screen.
     */
    public Point getPosition() {
        Point out = new Point(0, 0);

        // Get frame context location.
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);

            glfwGetWindowPos(window, w, h);
            out.x = w.get(0);
            out.y = h.get(0);
        }
        
        return out;
    }

    /**
     * VXRA API Method.
     * Return the mouse pointer location on screen.
     * 
     * @return The mouse pointer location relative to the window origin.
     */
    @Override
    public Point getPointerLocation() {
        Point mousePos = new Point(0, 0);

        try (MemoryStack stack = stackPush()) {
            DoubleBuffer x = stack.mallocDouble(1);
            DoubleBuffer y = stack.mallocDouble(1);

            glfwGetCursorPos(window, x, y);
            mousePos = new Point((int)Math.floor(x.get(0)), (int)Math.floor(y.get(0)));
        }

        return new Point(
            new Vector2(mousePos).mult(new Vector2(
                (float)virtualResolution.x / windowResolution.x, 
                (float)virtualResolution.y / windowResolution.y
            ))
        );
    }

    /**
     * For internal use only. Called by the render pipeline so the window
     * has access to the *window* event handler.
     * 
     * @param eventHandler Event handler for this window. 
     */
    public void setWindowEventHandler(GLEventHandler eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * Set the window's visibility on screen. Initially is not visible.
     * Due to GLFW (limitations?), this will only show the window on screen.
     * 
     * @param state Show window (if {@code true}) or hide it (if {@code false}).
     */
    public void setVisible(boolean state) {
        if (state)
            glfwShowWindow(window);
        else
            glfwHideWindow(window);
    }

    /**
     * VXRA API function. Immediately place this window in fullscreen.
     * 
     * @since VXRA 0.6a
     */
    @Override
    public void enterFullScreen() {
        try (MemoryStack stack = stackPush()) {
            long monitor = glfwGetPrimaryMonitor();

            GLFWVidMode vidMode = glfwGetVideoMode(monitor);
            glfwSetWindowMonitor(window, monitor, 5, 0, vidMode.width(), 
                                 vidMode.height(), GLFW_DONT_CARE);

            eventHandler.iUpdateResolution(vidMode.width(), vidMode.height());
        }
    }

    /**
     * VXRA API function. Immediately bring this window out of fullscreen.
     * 
     * @since VXRA 0.6a
     */
    @Override
    public void exitFullScreen() {
        try (MemoryStack stack = stackPush()) {
            Point wres = GlobalAppConfig.bcfg.APP_RES_DEFAULT;
            glfwSetWindowMonitor(window, NULL, 100, 100, wres.x, 
                                 wres.y, GLFW_DONT_CARE);

            eventHandler.iUpdateResolution(wres.x, wres.y);
        }
    }
}
