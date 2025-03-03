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
package de.bluecolored.bluemap.core.map.hires.blockmodel;

import com.flowpowered.math.TrigMath;
import com.flowpowered.math.vector.Vector2f;
import com.flowpowered.math.vector.Vector3f;
import com.flowpowered.math.vector.Vector3i;
import com.flowpowered.math.vector.Vector4f;
import de.bluecolored.bluemap.core.map.hires.BlockModelView;
import de.bluecolored.bluemap.core.map.hires.HiresTileModel;
import de.bluecolored.bluemap.core.map.hires.RenderSettings;
import de.bluecolored.bluemap.core.resourcepack.BlockColorCalculatorFactory;
import de.bluecolored.bluemap.core.resourcepack.ResourcePack;
import de.bluecolored.bluemap.core.resourcepack.blockmodel.BlockModelResource;
import de.bluecolored.bluemap.core.resourcepack.blockmodel.TransformedBlockModelResource;
import de.bluecolored.bluemap.core.resourcepack.texture.Texture;
import de.bluecolored.bluemap.core.util.Direction;
import de.bluecolored.bluemap.core.util.math.Color;
import de.bluecolored.bluemap.core.util.math.MatrixM4f;
import de.bluecolored.bluemap.core.util.math.VectorM2f;
import de.bluecolored.bluemap.core.util.math.VectorM3f;
import de.bluecolored.bluemap.core.world.BlockNeighborhood;
import de.bluecolored.bluemap.core.world.ExtendedBlock;
import de.bluecolored.bluemap.core.world.LightData;

/**
 * This model builder creates a BlockStateModel using the information from parsed resource-pack json files.
 */
public class ResourceModelBuilder {
    private static final float BLOCK_SCALE = 1f / 16f;

    private final BlockColorCalculatorFactory.BlockColorCalculator blockColorCalculator;
    private final RenderSettings renderSettings;

    private final VectorM3f[] corners = new VectorM3f[8];
    private final VectorM2f[] rawUvs = new VectorM2f[4];
    private final VectorM2f[] uvs = new VectorM2f[4];
    private final Color tintColor = new Color();
    private final Color mapColor = new Color();

    private BlockNeighborhood<?> block;
    private TransformedBlockModelResource blockModelResource;
    private BlockModelView blockModel;
    private Color blockColor;
    private float blockColorOpacity;

    public ResourceModelBuilder(ResourcePack resourcePack, RenderSettings renderSettings) {
        this.blockColorCalculator = resourcePack.getBlockColorCalculatorFactory().createCalculator();
        this.renderSettings = renderSettings;

        for (int i = 0; i < corners.length; i++) corners[i] = new VectorM3f(0, 0, 0);
        for (int i = 0; i < uvs.length; i++) rawUvs[i] = new VectorM2f(0, 0);
    }

    private final MatrixM4f modelTransform = new MatrixM4f();
    public void build(BlockNeighborhood<?> block, TransformedBlockModelResource bmr, BlockModelView blockModel, Color color) {
        this.block = block;
        this.blockModel = blockModel;
        this.blockColor = color;
        this.blockColorOpacity = 0f;
        this.blockModelResource = bmr;

        this.tintColor.set(0, 0, 0, -1, true);

        // render model
        int modelStart = blockModel.getStart();

        for (BlockModelResource.Element element : blockModelResource.getModel().getElements()) {
            buildModelElementResource(element, blockModel.initialize());
        }

        if (color.a > 0) {
            color.flatten();
            color.a = blockColorOpacity;
        }

        blockModel.initialize(modelStart);

        // apply model-rotation
        if (blockModelResource.hasRotation()) {
            blockModel.transform(modelTransform.identity()
                    .translate(-0.5f, -0.5f, -0.5f)
                    .multiplyTo(blockModelResource.getRotationMatrix())
                    .translate(0.5f, 0.5f, 0.5f)
            );
        }

        //random offset
        if (block.getProperties().isRandomOffset()){
            float dx = (hashToFloat(block.getX(), block.getZ(), 123984) - 0.5f) * 0.75f;
            float dz = (hashToFloat(block.getX(), block.getZ(), 345542) - 0.5f) * 0.75f;
            blockModel.translate(dx, 0, dz);
        }

    }

    private final MatrixM4f modelElementTransform = new MatrixM4f();
    private void buildModelElementResource(BlockModelResource.Element bmer, BlockModelView blockModel) {

        //create faces
        Vector3f from = bmer.getFrom();
        Vector3f to = bmer.getTo();

        float
                minX = Math.min(from.getX(), to.getX()),
                minY = Math.min(from.getY(), to.getY()),
                minZ = Math.min(from.getZ(), to.getZ()),
                maxX = Math.max(from.getX(), to.getX()),
                maxY = Math.max(from.getY(), to.getY()),
                maxZ = Math.max(from.getZ(), to.getZ());

        VectorM3f[] c = corners;
        c[0].x = minX; c[0].y = minY; c[0].z = minZ;
        c[1].x = minX; c[1].y = minY; c[1].z = maxZ;
        c[2].x = maxX; c[2].y = minY; c[2].z = minZ;
        c[3].x = maxX; c[3].y = minY; c[3].z = maxZ;
        c[4].x = minX; c[4].y = maxY; c[4].z = minZ;
        c[5].x = minX; c[5].y = maxY; c[5].z = maxZ;
        c[6].x = maxX; c[6].y = maxY; c[6].z = minZ;
        c[7].x = maxX; c[7].y = maxY; c[7].z = maxZ;

        int modelStart = blockModel.getStart();
        createElementFace(bmer, Direction.DOWN, c[0], c[2], c[3], c[1]);
        createElementFace(bmer, Direction.UP, c[5], c[7], c[6], c[4]);
        createElementFace(bmer, Direction.NORTH, c[2], c[0], c[4], c[6]);
        createElementFace(bmer, Direction.SOUTH, c[1], c[3], c[7], c[5]);
        createElementFace(bmer, Direction.WEST, c[0], c[1], c[5], c[4]);
        createElementFace(bmer, Direction.EAST, c[3], c[2], c[6], c[7]);
        blockModel.initialize(modelStart);

        //rotate and scale down
        blockModel.transform(modelElementTransform
                .copy(bmer.getRotationMatrix())
                .scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE)
        );
    }

    private final VectorM3f faceRotationVector = new VectorM3f(0, 0, 0);
    private void createElementFace(BlockModelResource.Element element, Direction faceDir, VectorM3f c0, VectorM3f c1, VectorM3f c2, VectorM3f c3) {
        BlockModelResource.Element.Face face = element.getFaces().get(faceDir);
        if (face == null) return;

        Vector3i faceDirVector = faceDir.toVector();

        // face culling
        if (face.getCullface() != null) {
            ExtendedBlock<?> b = getRotationRelativeBlock(face.getCullface());
            if (b.getProperties().isCulling()) return;
        }

        // light calculation
        ExtendedBlock<?> facedBlockNeighbor = getRotationRelativeBlock(faceDir);
        LightData blockLightData = block.getLightData();
        LightData facedLightData = facedBlockNeighbor.getLightData();

        int sunLight = Math.max(blockLightData.getSkyLight(), facedLightData.getSkyLight());
        int blockLight = Math.max(blockLightData.getBlockLight(), facedLightData.getBlockLight());

        // filter out faces that are in a "cave" that should not be rendered
        if (
                this.block.getY() < renderSettings.getRemoveCavesBelowY() &&
                (renderSettings.isCaveDetectionUsesBlockLight() ? blockLight : sunLight) == 0f
        ) return;

        // initialize the faces
        blockModel.initialize();
        blockModel.add(2);

        HiresTileModel tileModel = blockModel.getHiresTile();
        int face1 = blockModel.getStart();
        int face2 = face1 + 1;

        // ####### positions
        tileModel.setPositions(face1,
                c0.x, c0.y, c0.z,
                c1.x, c1.y, c1.z,
                c2.x, c2.y, c2.z
        );
        tileModel.setPositions(face2,
                c0.x, c0.y, c0.z,
                c2.x, c2.y, c2.z,
                c3.x, c3.y, c3.z
        );

        // ####### texture
        Texture texture = face.getTexture();
        int textureId = texture.getId();
        tileModel.setMaterialIndex(face1, textureId);
        tileModel.setMaterialIndex(face2, textureId);

        // ####### UV
        Vector4f uvRaw = face.getUv();
        float
                uvx = uvRaw.getX() / 16f,
                uvy = uvRaw.getY() / 16f,
                uvz = uvRaw.getZ() / 16f,
                uvw = uvRaw.getW() / 16f;

        rawUvs[0].set(uvx, uvw);
        rawUvs[1].set(uvz, uvw);
        rawUvs[2].set(uvz, uvy);
        rawUvs[3].set(uvx, uvy);

        // face-rotation
        int rotationSteps = Math.floorDiv(face.getRotation(), 90) % 4;
        if (rotationSteps < 0) rotationSteps += 4;
        for (int i = 0; i < 4; i++)
            uvs[i] = rawUvs[(rotationSteps + i) % 4];

        // UV-Lock counter-rotation
        float uvRotation = 0f;
        if (blockModelResource.isUVLock() && blockModelResource.hasRotation()) {
            Vector2f rotation = blockModelResource.getRotation();

            float xRotSin = TrigMath.sin(rotation.getX() * TrigMath.DEG_TO_RAD);
            float xRotCos = TrigMath.cos(rotation.getX() * TrigMath.DEG_TO_RAD);

            uvRotation =
                    rotation.getY() * (faceDirVector.getY() * xRotCos + faceDirVector.getZ() * xRotSin) +
                    rotation.getX() * (1 - faceDirVector.getY());
        }

        // rotate uv's
        if (uvRotation != 0){
            uvRotation *= TrigMath.DEG_TO_RAD;
            float cx = TrigMath.cos(uvRotation), cy = TrigMath.sin(uvRotation);
            for (VectorM2f uv : uvs) {
                uv.translate(-0.5f, -0.5f);
                uv.rotate(cx, cy);
                uv.translate(0.5f, 0.5f);
            }
        }

        tileModel.setUvs(face1,
                uvs[0].x, uvs[0].y,
                uvs[1].x, uvs[1].y,
                uvs[2].x, uvs[2].y
        );

        tileModel.setUvs(face2,
                uvs[0].x, uvs[0].y,
                uvs[2].x, uvs[2].y,
                uvs[3].x, uvs[3].y
        );


        // ####### face-tint
        if (face.isTinted()) {
            if (tintColor.a < 0) {
                blockColorCalculator.getBlockColor(block, tintColor);
            }

            tileModel.setColor(face1, tintColor.r, tintColor.g, tintColor.b);
            tileModel.setColor(face2, tintColor.r, tintColor.g, tintColor.b);
        } else {
            tileModel.setColor(face1, 1, 1, 1);
            tileModel.setColor(face2, 1, 1, 1);
        }

        // ####### blocklight
        tileModel.setBlocklight(face1, blockLight);
        tileModel.setBlocklight(face2, blockLight);

        // ####### sunlight
        tileModel.setSunlight(face1, sunLight);
        tileModel.setSunlight(face2, sunLight);

        // ######## AO
        float ao0 = 1f, ao1 = 1f, ao2 = 1f, ao3 = 1f;
        if (blockModelResource.getModel().isAmbientOcclusion()){
            ao0 = testAo(c0, faceDir);
            ao1 = testAo(c1, faceDir);
            ao2 = testAo(c2, faceDir);
            ao3 = testAo(c3, faceDir);
        }

        tileModel.setAOs(face1, ao0, ao1, ao2);
        tileModel.setAOs(face2, ao0, ao2, ao3);

        //if is top face set model-color
        faceRotationVector.set(
                faceDirVector.getX(),
                faceDirVector.getY(),
                faceDirVector.getZ()
        );
        makeRotationRelative(faceRotationVector);
        faceRotationVector.rotateAndScale(element.getRotationMatrix());

        float a = faceRotationVector.y;
        if (a > 0){
            mapColor.set(texture.getColorPremultiplied());
            if (tintColor.a >= 0) {
                mapColor.multiply(tintColor);
            }

            // apply light
            float combinedLight = Math.max(sunLight / 15f, blockLight / 15f);
            combinedLight = (1 - renderSettings.getAmbientLight()) * combinedLight + renderSettings.getAmbientLight();
            mapColor.r *= combinedLight;
            mapColor.g *= combinedLight;
            mapColor.b *= combinedLight;

            if (mapColor.a > blockColorOpacity)
                blockColorOpacity = mapColor.a;

            blockColor.add(mapColor);
        }
    }

    private ExtendedBlock<?> getRotationRelativeBlock(Direction direction){
        return getRotationRelativeBlock(direction.toVector());
    }

    private ExtendedBlock<?> getRotationRelativeBlock(Vector3i direction){
        return getRotationRelativeBlock(
                direction.getX(),
                direction.getY(),
                direction.getZ()
        );
    }

    private final VectorM3f rotationRelativeBlockDirection = new VectorM3f(0, 0, 0);
    private ExtendedBlock<?> getRotationRelativeBlock(int dx, int dy, int dz){
        rotationRelativeBlockDirection.set(dx, dy, dz);
        makeRotationRelative(rotationRelativeBlockDirection);

        return block.getNeighborBlock(
                Math.round(rotationRelativeBlockDirection.x),
                Math.round(rotationRelativeBlockDirection.y),
                Math.round(rotationRelativeBlockDirection.z)
        );
    }

    private void makeRotationRelative(VectorM3f direction){
        direction.transform(blockModelResource.getRotationMatrix());
    }

    private float testAo(VectorM3f vertex, Direction dir){
        Vector3i dirVec = dir.toVector();
        int occluding = 0;

        int x = 0;
        if (vertex.x == 16){
            x = 1;
        } else if (vertex.x == 0){
            x = -1;
        }

        int y = 0;
        if (vertex.y == 16){
            y = 1;
        } else if (vertex.y == 0){
            y = -1;
        }

        int z = 0;
        if (vertex.z == 16){
            z = 1;
        } else if (vertex.z == 0){
            z = -1;
        }


        if (x * dirVec.getX() + y * dirVec.getY() > 0){
            if (getRotationRelativeBlock(x, y, 0).getProperties().isOccluding()) occluding++;
        }

        if (x * dirVec.getX() + z * dirVec.getZ() > 0){
            if (getRotationRelativeBlock(x, 0, z).getProperties().isOccluding()) occluding++;
        }

        if (y * dirVec.getY() + z * dirVec.getZ() > 0){
            if (getRotationRelativeBlock(0, y, z).getProperties().isOccluding()) occluding++;
        }

        if (x * dirVec.getX() + y * dirVec.getY() + z * dirVec.getZ() > 0){
            if (getRotationRelativeBlock(x, y, z).getProperties().isOccluding()) occluding++;
        }

        if (occluding > 3) occluding = 3;
        return  Math.max(0f, Math.min(1f - occluding * 0.25f, 1f));
    }

    private static float hashToFloat(int x, int z, long seed) {
        final long hash = x * 73428767 ^ z * 4382893 ^ seed * 457;
        return (hash * (hash + 456149) & 0x00ffffff) / (float) 0x01000000;
    }

}
