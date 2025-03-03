/*
 * This file is part of BlueMap, licensed under the MIT License (MIT).
 *
 * Copyright (c) Blue (Lukas Rieger) <https://bluecolored.de>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package de.bluecolored.bluemap.common.api.marker;

import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.marker.Shape;
import de.bluecolored.bluemap.api.marker.ShapeMarker;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.awt.*;
import java.util.List;
import java.util.Objects;

public class ShapeMarkerImpl extends ObjectMarkerImpl implements ShapeMarker {
    public static final String MARKER_TYPE = "shape";

    private Shape shape;
    private float shapeY;
    private boolean depthTest;
    private int lineWidth;
    private Color lineColor, fillColor;

    private boolean hasUnsavedChanges;

    public ShapeMarkerImpl(String id, BlueMapMap map, Vector3d position, Shape shape, float shapeY) {
        super(id, map, position);

        Objects.requireNonNull(shape);

        this.shape = shape;
        this.shapeY = shapeY;
        this.lineWidth = 2;
        this.lineColor = new Color(255, 0, 0, 200);
        this.fillColor = new Color(200, 0, 0, 100);

        this.hasUnsavedChanges = true;
    }

    @Override
    public String getType() {
        return MARKER_TYPE;
    }

    @Override
    public Shape getShape() {
        return this.shape;
    }

    @Override
    public float getShapeY() {
        return this.shapeY;
    }

    @Override
    public synchronized void setShape(Shape shape, float shapeY) {
        Objects.requireNonNull(shape);

        this.shape = shape;
        this.shapeY = shapeY;
        this.hasUnsavedChanges = true;
    }

    @Override
    public boolean isDepthTestEnabled() {
        return this.depthTest;
    }

    @Override
    public void setDepthTestEnabled(boolean enabled) {
        this.depthTest = enabled;
        this.hasUnsavedChanges = true;
    }

    @Override
    public int getLineWidth() {
        return lineWidth;
    }

    @Override
    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        this.hasUnsavedChanges = true;
    }

    @Override
    public Color getLineColor() {
        return this.lineColor;
    }

    @Override
    public synchronized void setLineColor(Color color) {
        Objects.requireNonNull(color);

        this.lineColor = color;
        this.hasUnsavedChanges = true;
    }

    @Override
    public Color getFillColor() {
        return this.fillColor;
    }

    @Override
    public synchronized void setFillColor(Color color) {
        Objects.requireNonNull(color);

        this.fillColor = color;
        this.hasUnsavedChanges = true;
    }

    @Override
    public void load(BlueMapAPI api, ConfigurationNode markerNode, boolean overwriteChanges) throws MarkerFileFormatException {
        super.load(api, markerNode, overwriteChanges);

        if (!overwriteChanges && hasUnsavedChanges) return;
        this.hasUnsavedChanges = false;

        this.shape = readShape(markerNode.node("shape"));
        this.shapeY = (float) markerNode.node("shapeY").getDouble(markerNode.node("height").getDouble(64)); // fallback to deprecated "height"
        this.depthTest = markerNode.node("depthTest").getBoolean(true);
        this.lineWidth = markerNode.node("lineWidth").getInt(2);

        ConfigurationNode lineColorNode = markerNode.node("lineColor");
        if (lineColorNode.virtual()) lineColorNode = markerNode.node("borderColor"); // fallback to deprecated "borderColor"
        this.lineColor = readColor(lineColorNode);

        this.fillColor = readColor(markerNode.node("fillColor"));
    }

    @Override
    public void save(ConfigurationNode markerNode) throws SerializationException {
        super.save(markerNode);

        writeShape(markerNode.node("shape"), this.shape);
        markerNode.node("shapeY").set(Math.round(shapeY * 1000f) / 1000f);
        markerNode.node("depthTest").set(this.depthTest);
        markerNode.node("lineWidth").set(this.lineWidth);
        writeColor(markerNode.node("lineColor"), this.lineColor);
        writeColor(markerNode.node("fillColor"), this.fillColor);

        hasUnsavedChanges = false;
    }

    private Shape readShape(ConfigurationNode node) throws MarkerFileFormatException {
        List<? extends ConfigurationNode> posNodes = node.childrenList();

        if (posNodes.size() < 3) throw new MarkerFileFormatException("Failed to read shape: point-list has fewer than 3 entries!");

        Vector2d[] positions = new Vector2d[posNodes.size()];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = readShapePos(posNodes.get(i));
        }

        return new Shape(positions);
    }

    private static Vector2d readShapePos(ConfigurationNode node) throws MarkerFileFormatException {
        ConfigurationNode nx, nz;
        nx = node.node("x");
        nz = node.node("z");

        if (nx.virtual() || nz.virtual()) throw new MarkerFileFormatException("Failed to read shape position: Node x or z is not set!");

        return new Vector2d(
                nx.getDouble(),
                nz.getDouble()
            );
    }

    private static Color readColor(ConfigurationNode node) throws MarkerFileFormatException {
        ConfigurationNode nr, ng, nb, na;
        nr = node.node("r");
        ng = node.node("g");
        nb = node.node("b");
        na = node.node("a");

        if (nr.virtual() || ng.virtual() || nb.virtual()) throw new MarkerFileFormatException("Failed to read color: Node r,g or b is not set!");

        float alpha = (float) na.getDouble(1);
        if (alpha < 0 || alpha > 1) throw new MarkerFileFormatException("Failed to read color: alpha value out of range (0-1)!");

        try {
            return new Color(nr.getInt(), ng.getInt(), nb.getInt(), (int)(alpha * 255));
        } catch (IllegalArgumentException ex) {
            throw new MarkerFileFormatException("Failed to read color: " + ex.getMessage(), ex);
        }
    }

    private static void writeShape(ConfigurationNode node, Shape shape) throws SerializationException {
        for (int i = 0; i < shape.getPointCount(); i++) {
            ConfigurationNode pointNode = node.appendListNode();
            Vector2d point = shape.getPoint(i);
            pointNode.node("x").set(Math.round(point.getX() * 1000d) / 1000d);
            pointNode.node("z").set(Math.round(point.getY() * 1000d) / 1000d);
        }
    }

    private static void writeColor(ConfigurationNode node, Color color) throws SerializationException {
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        float a = color.getAlpha() / 255f;

        node.node("r").set(r);
        node.node("g").set(g);
        node.node("b").set(b);
        node.node("a").set(a);
    }

}
