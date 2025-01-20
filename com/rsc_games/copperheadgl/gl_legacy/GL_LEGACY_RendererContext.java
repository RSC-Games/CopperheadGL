package com.rsc_games.copperheadgl.gl_legacy;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glLineWidth;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;

import org.joml.Vector2f;
import org.joml.Vector3f;

import velocity.Rect;
import velocity.renderer.RendererImage;
import velocity.util.Point;

class GL_LEGACY_RendererContext {
    public static CopperheadGL renderPipeline;

    // Text rendering workaround.
    private static final BufferedImage __b = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    private static final Graphics __g = __b.getGraphics();
    ArrayList<GLTexture2D> fontTextures = new ArrayList<GLTexture2D>();
    HashMap<String, GLTexture2D> rectTextureCache = new HashMap<String, GLTexture2D>();

    // Renderer limits and constants.
    private static final int MAX_LIGHTS = 64;

    // Internal framebuffer representation.
    private int width;
    private int height;

    private OGLDrawQueue drawQueue;
    private OGLDrawQueue uiDrawQueue;
    private OGLFrameBuffer backBuffer;
    private OGLFrameBuffer uiBackBuffer;
    private GLTextureCache texCache;
    private GLBatchSystem batch;
    private GLPrimitiveRenderer primitiveRenderer;

    // Shader Stack.
    // TODO: Relocate shader stack and batch renderer into
    // its own file.
    private GLShader texturedShader;
    private GLShader litShader;
    private GLShader primitiveShader;

    public OGLRendererContext(int w, int h) {
        this.batch = new GLBatchSystem();
        this.primitiveRenderer = new GLPrimitiveRenderer();
        this.width = w;
        this.height = h;

        // Set up the backbuffer system.
        this.drawQueue = new OGLDrawQueue();
        this.uiDrawQueue = new OGLDrawQueue();
        this.backBuffer = new OGLFrameBuffer(w, h, drawQueue);
        this.uiBackBuffer = new OGLFrameBuffer(w, h, uiDrawQueue);
    }

    public OGLFrameBuffer getBackBuffer() { return backBuffer; }
    public OGLFrameBuffer getUIBackBuffer() { return uiBackBuffer; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setWidth(int nWidth) { this.width = nWidth; }
    public void setHeight(int nHeight) { this.height = nHeight; }

    /**
     * Initialize the graphics device and all used data structures.
     * 
     * @implNote THIS FUNCTION MUST BE CALLED AFTER OPENGL DEVICE INIT.
     *   OTHERWISE, IT COULD CRASH THE KERNEL AND YOUR OPERATING SYSTEM!
     */
    public void init() {
        Point winRes = renderPipeline.window.getResolution();
        updateResolution(winRes.x, winRes.y);

        texCache = new GLTextureCache();
        batch.init();
        primitiveRenderer.init();

        // Alter current blend function.
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Compile and link shaders (really?)
        // TODO: Batch unlit and lit shader calls separately.
        texturedShader = ShaderUtil.compileShader("./shader/textured/textured.frag", 
                                                  "./shader/textured/textured.vert");
        litShader = ShaderUtil.compileShader("./shader/lit/lit.frag", 
                                             "./shader/lit/lit.vert");
        primitiveShader = ShaderUtil.compileShader("./shader/primitive/primitive.frag", 
                                                   "./shader/primitive/primitive.vert");

        // Generate samplers for the shader context.
        int[] samplers = batch.genSamplers();
        provisionShader(texturedShader, samplers);
        provisionShader(litShader, samplers);
        provisionShader(primitiveShader, samplers);

        // Set the backbuffer clear color.
        glClearColor(0f, 0f, 0f, 0f);
    }

    // Merge these samplers together for both shaders?
    private void provisionShader(GLShader shader, int[] samplers) {
        ShaderUtil.bind(shader);

        ShaderUtil.SetIntegerArr("u_Textures", samplers);
        System.out.println("[dbg]: Shader: Bound shader " + shader.getID());
    }

    /**
     * Render the game frame from the submitted drawcalls.
     */
    public void renderFrame() {
        // Clear the font textures.
        for (GLTexture2D fontTex : fontTextures) {
            fontTex.free();
        }
        fontTextures.clear();

        // Purge the rect cache.
        for (GLTexture2D tex : rectTextureCache.values())
            tex.free();

        rectTextureCache.clear();

        // Generate the lighting buffer data and write it to the
        // lighting shader.
        // BUGFIX: Upload light data specifically to the lit shader.
        // TODO: Eventually update so data is only uploaded once a frame 
        // when the shader is first bound that frame.
        ShaderUtil.bind(litShader);
        OGLLightingEngine ole = (OGLLightingEngine)renderPipeline.le;
        ole.writePipelineData(litShader, MAX_LIGHTS);

        // Render the scene.
        dispatchDrawCalls(this.drawQueue);
        this.drawQueue.clear();

        // PRE-UI SHADING HERE?

        // Render the UI.
        dispatchDrawCalls(this.uiDrawQueue);
        this.uiDrawQueue.clear();
    }

    /**
     * Dispatch a set of drawcalls and render them.
     * 
     * @param queue Queue to consume calls from.
     */
    private void dispatchDrawCalls(OGLDrawQueue queue) {
        batch.start();

        // Note: executeCall will autobind the required shader.
        for (ArrayList<OGLDrawCall> layer : queue.getDrawCalls().values()) {
            for (OGLDrawCall call : layer) {
                executeCall(call);
            }
        }

        // Flush the current call data.
        batch.commit();
    }

    /**
     * Execute a drawcall.
     * 
     * @param call Drawcall to execute.
     */
    private void executeCall(OGLDrawCall call) {
        Object[] args = call.getParameters();

        switch (call.type) {
            case DRAW_BLIT:
                drawTexture((OGLRendererImage)args[0], (Point)args[1], (float)args[2]);
                break;
            case DRAW_SHADE:
                drawShaded((OGLRendererImage)args[0], (Point)args[1], (float)args[2]);
                break;
            case DRAW_CIRCLE:
                break;
            case DRAW_LINE:
                drawLine((Point)args[0], (Point)args[1], (int)args[2], (Color)args[3]);
                break;
            case DRAW_LINES:
                break;
            case DRAW_RECT:
                drawRect((Rect)args[0], (int)args[1], (Color)args[2], (boolean)args[3]);
                break;
            case DRAW_TEXT:
                renderText((Point)args[0], (String)args[1], (Font)args[2], (Color)args[3]);
                break;
            case DRAW_TRI:
                break;
        }
    }

    /**
     * Draw a line on screen.
     * 
     * @param p1 Starting point.
     * @param p2 Ending point.
     * @param weight Line weight.
     * @param c Color.
     */
    private void drawLine(Point p1, Point p2, int weight, Color c) {
        // TODO: Primitive and texture batch renderer interoperability is broken.
        glLineWidth(weight);
        changeShaderSafe(primitiveShader);
        primitiveRenderer.batchUnfilled(new Point[] {p1, p2}, c);
        glLineWidth(1);
    }

    /**
     * Draw an unshaded texture on screen.
     * 
     * @param img The image to draw.
     * @param pos The location to draw it at.
     * @param rot The rotation of the image.
     */
    private void drawTexture(OGLRendererImage img, Point pos, float rot) {
        GLTexture2D tex = texCache.getGLImage(img);
        int w = img.getWidth();
        int h = img.getHeight();

        // Verify shader and buffer state.
        changeShaderSafe(texturedShader);
        batch.batchQuad(tex, pos, w, h);
    }

    /**
     * Draw a shaded texture on screen.
     * 
     * @param img The image to draw.
     * @param pos The location to draw it at.
     * @param rot The rotation of the image.
     */
    private void drawShaded(OGLRendererImage img, Point pos, float rot) {
        GLTexture2D tex = texCache.getGLImage(img);
        int w = img.getWidth();
        int h = img.getHeight();

        // Verify shader and buffer state.
        changeShaderSafe(litShader);
        batch.batchQuad(tex, pos, w, h);
    }

    /**
     * Draw a rectangle on screen. Currently just abuses the texture
     * renderer.
     * 
     * @param r The rect to draw
     * @param weight Line width
     * @param c Rect color
     * @param filled Whether to fill the rectangle with color.
     */
    // TODO: Entire pipeline rewrite. This is horribly inefficient.
    private void drawRect(Rect r, int weight, Color c, boolean filled) {
        // Ensure we're using the textured shader.
        changeShaderSafe(texturedShader);

        String index = r.getWH().toString() + "~" + c.toString();
        GLTexture2D renderableRect = rectTextureCache.get(index);
        int w = r.getW();
        int h = r.getH();

        if (renderableRect == null) {
            // Create a buffered image as the rect.
            BufferedImage renderedRect = new BufferedImage(
                w, h, 
                BufferedImage.TYPE_4BYTE_ABGR
            );

            //System.out.printf("Got w/h for textbox %d, %d\n", w, h);
            Graphics rrg = renderedRect.getGraphics();
            rrg.setColor(c);
            rrg.drawRect(0, 0, w-1, h-1);

            // Upload the texture and retain it.
            renderableRect = new GLTexture2D(renderedRect);
            rectTextureCache.put(index, renderableRect);
        }

        // Verify shader and buffer state.
        batch.batchQuad(renderableRect, r.getDrawLoc(), w, h);
        fontTextures.add(renderableRect);

        // WORKAROUND: Text uses the wrong texture id for some reason.
        // Flush the draw buffers.
        batch.commit();
        batch.start();
    }

    /**
     * Draw text on screen. Currently uses AWT and uploads a texture every frame
     * per text line drawn. Also requires a batch flush every time for bug workarounds.
     * Generally just a hell of a function.
     * 
     * @param pos Text location.
     * @param text Text string.
     * @param font Font to use.
     * @param color Text color.
     */
    private void renderText(Point pos, String text, Font font, Color color) {
        // Ensure we're using the textured shader.
        changeShaderSafe(texturedShader);

        __g.setFont(font);
        FontMetrics metrics = __g.getFontMetrics();
        int fontWidth = metrics.stringWidth(text);
        java.awt.geom.Rectangle2D fontRect = metrics.getStringBounds(text, __g);

        // Create a buffered image to draw text to for GL rendering.
        BufferedImage renderedText = new BufferedImage(
            Math.max(fontWidth * 2, 1),
            Math.max((int)fontRect.getHeight(), 1), 
            BufferedImage.TYPE_4BYTE_ABGR
        );
        int w = renderedText.getWidth();
        int h = renderedText.getHeight();

        //System.out.printf("Got w/h for textbox %d, %d\n", w, h);
        Graphics rtg = renderedText.getGraphics();
        rtg.setFont(font);
        rtg.setColor(color);
        rtg.drawString(text, 0, h - metrics.getDescent());

        // Adjust the text location to what is expected application-side.
        GLTexture2D tex = new GLTexture2D(renderedText);

        // Verify shader and buffer state.
        pos = pos.sub(new Point(0, h - metrics.getDescent()));
        batch.batchQuad(tex, pos, w, h);
        fontTextures.add(tex);

        // WORKAROUND: Text uses the wrong texture id for some reason.
        // Flush the draw buffers.
        batch.commit();
        batch.start();
    }

    private void changeShaderSafe(GLShader newShader) {
        boolean change = newShader != ShaderUtil.getCurrent();

        batch.flushConditional(change);
        //primitiveRenderer.flushConditional(change);

        // Change the shader type if there's anything to batch.
        if (change) {
            ShaderUtil.bind(newShader);
            // TODO: Init all shaders once a frame (upload uniforms and stuff).
        }
    }

    public void deinit() {
        batch.deinit();
        primitiveRenderer.deinit();
    }

    public void clearBackBuffer() {
        glClear(GL_COLOR_BUFFER_BIT);
    }

    /**
     * Internally load an image, intern it, then upload it to the GPU.
     * 
     * @param img The loaded image.
     * @param path The image path.
     * @return The loaded image.
     */
    public RendererImage loadImage(BufferedImage img, String path) {
        return texCache.getInternedReference(img, path);
    }

    /**
     * Force a texture cache GC Run.
     */
    public void gc() {
        texCache.textureGC();
    }

    /**
     * Update the current tracked renderer resolution.
     * 
     * @param w New width.
     * @param h New height.
     */
    public void updateResolution(int w, int h) {
        this.width = w;
        this.height = h;
        this.backBuffer.resize(w, h);
        this.uiBackBuffer.resize(w, h);
        GLUtil.resize(w, h);
    }
}

class TexturedVertexInfo {
    /**
     * Vertex size. Comprised of multiple components.
     * 3: SIZEOF pos struct. The pos[] has 3 floats.
     * 4: SIZEOF color data. Color has 4 floats.
     * 2: SIZEOF texcoords. Texture data has 2 floats.
     * 1: SIZEOF texid. Texture ID (for lookup) is 1 float.
     */
    public static final int FLOAT_CNT = 3 + 4 + 2 + 1;
    public static final int SIZEOF = FLOAT_CNT * Float.BYTES;

    // Attribute offset of vertex info
    // These values are expressed in total bytes -> (n*Float.BYTES)
    public static final int POS_OFFSET = 0;
    public static final int COLOR_OFFSET = 3*Float.BYTES;
    public static final int TEX_COORD_OFFSET = (3+4)*Float.BYTES;
    public static final int TEX_ID_OFFSET = (3+4+2)*Float.BYTES;

    public final float[] pos;
    public final float[] color;
    public final float[] texCoords;
    public final float texID;

    public TexturedVertexInfo(Vector3f pos, float[] color, Vector2f texCoords, float texID) {
        this.pos = new float[] { pos.x, pos.y, pos.z };
        this.color = color;
        this.texCoords = new float[] { texCoords.x, texCoords.y };
        this.texID = texID;
    }

    public float[] ToFloatArray() {
        float[] output = new float[TexturedVertexInfo.FLOAT_CNT];
        output[0] = pos[0];
        output[1] = pos[1];
        output[2] = pos[2];

        output[3] = color[0];
        output[4] = color[1];
        output[5] = color[2];
        output[6] = color[3];

        output[7] = texCoords[0];
        output[8] = texCoords[1];

        output[9] = texID;
        return output;
    }
}

class PrimitiveVertexInfo {
    /**
     * Vertex size. Comprised of multiple components.
     * 3: SIZEOF pos struct. The pos[] has 3 floats.
     * 4: SIZEOF color data. Color has 4 floats.
     */
    public static final int FLOAT_CNT = 3 + 4 + 2 + 1;
    public static final int SIZEOF = FLOAT_CNT * Float.BYTES;

    // Attribute offset of vertex info
    // These values are expressed in total bytes -> (n*Float.BYTES)
    public static final int POS_OFFSET = 0;
    public static final int COLOR_OFFSET = 3*Float.BYTES;

    public final float[] pos;
    public final float[] color;

    public PrimitiveVertexInfo(Vector3f pos, Color c) {
        this.pos = new float[] { pos.x, pos.y, pos.z };
        float[] cd = new float[] {1f, 1f, 1f, 1f};
        //c.getColorComponents(cd);
        this.color = cd;
    }

    // Offset so I can reuse the same vertex array object.
    // Does waste some memory but that can be resolved later.
    public float[] ToFloatArray() {
        float[] output = new float[TexturedVertexInfo.FLOAT_CNT];
        output[0] = pos[0];
        output[1] = pos[1];
        output[2] = pos[2];

        output[3] = color[0];
        output[4] = color[1];
        output[5] = color[2];
        output[6] = color[3];
        return output;
    }
}