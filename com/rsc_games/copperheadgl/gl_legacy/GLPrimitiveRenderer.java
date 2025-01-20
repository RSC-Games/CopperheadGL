package com.rsc_games.copperheadgl.gl_legacy;

import static org.lwjgl.opengl.GL33C.*;
import org.joml.Vector3f;

import java.awt.Color;

import velocity.util.Point;

class GLPrimitiveRenderer {
    private static final int MAX_VERTICES = 10000;
    private static final int MAX_INDICES = 20000;

    private int quadVBO;
    private int quadVAO;
    private int quadIBO; // Index Buffer Object

    private float[] vertices;
    private int vertexCount;
    private int[] indices;
    private int indexCount;

    /**
     * Create a primitive batch renderer context.
     */
    public GLPrimitiveRenderer() {
        // Query maximum textures.
        this.quadVAO = 0;
        this.quadVBO = 0;
        this.quadIBO = 0;

        this.vertices = null;
        this.vertexCount = 0;
        this.indices = null;
        this.indexCount = 0;
    }

    public void init() {
        // Generate the VAO.
        quadVAO = glGenVertexArrays();
        glBindVertexArray(quadVAO);

        // Generate the VBO.
        quadVBO = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        vertices = new float[PrimitiveVertexInfo.FLOAT_CNT * MAX_VERTICES];
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_DYNAMIC_DRAW);

        // Generate the IBO.
        indices = new int[MAX_INDICES];

        quadIBO = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadIBO);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_DYNAMIC_DRAW);

        // Describe the arrays. (I think like D3D_BUFFER_DESC or something)
        glVertexAttribPointer(0, 3, GL_FLOAT, false, PrimitiveVertexInfo.SIZEOF, PrimitiveVertexInfo.POS_OFFSET);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(1, 4, GL_FLOAT, false, PrimitiveVertexInfo.SIZEOF, PrimitiveVertexInfo.COLOR_OFFSET);
        glEnableVertexAttribArray(1);
    }

    /**
     * Flush the buffers if necessary.
     * 
     * @param shaderType
     */
    public void flushConditional(boolean shaderTypeAltered) {
        // Flush the buffers if all of our available space has been used.
        if (indexCount >= MAX_INDICES || vertexCount >= MAX_VERTICES || shaderTypeAltered) {
            //System.out.println("[dbg]: Buffers full/shader changed! Flushing to GPU!");
            commit();
            start();
        }
    }

    /**
     * Render a primitive. Uses the currently bound shader for the batch
     * operation. 
     * 
     * @param points Location of each point.
     * @param c Vertex color.
     */
    public void batchUnfilled(Point[] points, Color c) {
        start();
        int startIndexCount = indexCount;

        // Convert the points to 3D vertices.
        Vector3f[] v3fvertices = new Vector3f[points.length];
        for (int i = 0; i < points.length; i++) {
            Point p = points[i];
            v3fvertices[i] = new Vector3f(p.x, p.y, 0f);
        }

        // Build the vertex info.
        for (int i = 0; i < v3fvertices.length; i++) {
            Vector3f current = GLUtil.toNDC(v3fvertices[i]);
            PrimitiveVertexInfo vi = new PrimitiveVertexInfo(
                current, 
                c
            );

            float[] vifs = vi.ToFloatArray();
            for (int j = 0; j < vifs.length; j++) {
                vertices[vertexCount * PrimitiveVertexInfo.FLOAT_CNT + j] = vifs[j];
            }
            vertexCount++;

            indices[indexCount + 0] = startIndexCount + i;
            indexCount++;
        }

        // Build the indexes.
        /*
        for (int i = 0; i < v3fvertices.length; i += 3) {
            indices[indexCount + 0] = startIndexCount + i + 0;
            indices[indexCount + 1] = startIndexCount + i + 1;
            indices[indexCount + 2] = startIndexCount + i + 2;
            indexCount += 3;
        }*/

        /*
        int vcount = 0;
        for (int i = 0; i < indices.length; i += 6) {
            indices[i+0] = 0+vcount;
            indices[i+1] = 1+vcount;
            indices[i+2] = 2+vcount;

            indices[i+3] = 0+vcount;
            indices[i+4] = 2+vcount;
            indices[i+5] = 3+vcount;
            vcount += 4;
        }
        */
        // No batching occurs here.
        //indexCount += 6;
        commit();
    }

    /**
     * Start a batched rendering operation.
     */
    public void start() {
        this.vertexCount = 0;
    }

    /**
     * Commit a currently active batch rendering operation to the
     * GPU.
     */
    public void commit() {
        batchEnd();
        batchFlush();
    }

    /**
     * Flush the textures and the VBO to the GPU. Bind the required
     * textures and write out the VAO.
     */
    private void batchFlush() {
        glBindVertexArray(quadVAO);
        glDrawElements(GL_LINES, indexCount, GL_UNSIGNED_INT, 0);
        indexCount = 0;
    }

    /**
     * Write the buffers to the GPU.
     */
    private void batchEnd() {
        glBindBuffer(GL_ARRAY_BUFFER, quadVBO);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, quadIBO);
        glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, 0, indices);
    }

    public void deinit() {
        glDeleteVertexArrays(quadVAO);
        glDeleteBuffers(quadVBO);
        glDeleteBuffers(quadIBO);
    }
}
