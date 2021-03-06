/*
 * This file is part of Caustic API.
 *
 * Copyright (c) 2013 Spout LLC <http://www.spout.org/>
 * Caustic API is licensed under the Spout License Version 1.
 *
 * Caustic API is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * In addition, 180 days after any changes are published, you can use the
 * software, incorporating those changes, under the terms of the MIT license,
 * as described in the Spout License Version 1.
 *
 * Caustic API is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License,
 * the MIT license and the Spout License Version 1 along with this program.
 * If not, see <http://www.gnu.org/licenses/> for the GNU Lesser General Public
 * License and see <http://spout.in/licensev1> for the full license, including
 * the MIT license.
 */
package org.spout.renderer.api.util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.flowpowered.math.vector.Vector3f;

import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * A static loading class for the COLLADA file format (.dae). This class has the capability to load mesh data such as positions, texture coordinates, and normals. All models should be triangulated.
 * Apart from geometry, the COLLADA file format also allows for joint descriptions as well as animation descriptions. COLLADA also allows for physical properties to be assigned to a model which can be
 * loaded by this class as well.
 */
public final class ColladaFileLoader {
    private static final Logger logger = CausticUtil.getCausticLogger();
    public static final String SEMANTIC_VERTEX = "VERTEX";
    public static final String SEMANTIC_NORMAL = "NORMAL";
    public static final String SEMANTIC_TEXCOORD = "TEXCOORD";
    public static final String SEMANTIC_POSITION = "POSITION";
    public static final String ELEMENT_MESH = "mesh";
    public static final String ELEMENT_TRIANGLES = "triangles";
    public static final String ELEMENT_INDICES = "p";
    public static final String ELEMENT_INPUT = "input";
    public static final String ELEMENT_FLOAT_ARRAY = "float_array";
    public static final String ATTRIBUTE_SEMANTIC = "semantic";
    public static final String ATTRIBUTE_SOURCE = "source";
    public static final String ATTRIBUTE_OFFSET = "offset";
    public static final String ARRAY_SEPARATOR = " ";
    public static final int STEP_TEXCOORD = 2;
    public static final int STEP_NORMAL = 3;

    private ColladaFileLoader() {
    }

    /**
     * Loads the .dae file into the provided lists. The file is parsed using DOM as COLLADA is valid XML. This method only loads the mesh data into the provided list. The input stream is closed after
     * loading all data. The Vector3 returned contains the new sizes for each of the provided float lists.
     *
     * @param in input stream to load data from
     * @param positions list to store positions
     * @param textureCoords list to store texture coordinates
     * @param normals list to store normals
     * @param indices list to store indices
     * @return vec3 the sizes of each component
     */
    public static Vector3f loadMesh(InputStream in, TFloatList positions, TFloatList textureCoords, TFloatList normals, TIntList indices) {
        try {
            // load the doc into memory
            final DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = builderFactory.newDocumentBuilder();
            final Document doc = builder.parse(in);
            doc.getDocumentElement().normalize();

            positions.clear();
            textureCoords.clear();
            normals.clear();
            indices.clear();

            // find where everything is stored in the document by id
            // NOTE: Blender's Collada exporter has a bug that doesn't change 'polylist' to 'triangles'
            // even if the mesh is triangulated.
            final Element meshTag = (Element) doc.getElementsByTagName(ELEMENT_MESH).item(0);
            final Map<String, String> semanticMap = new HashMap<>();

            final TObjectIntMap<String> offsetMap = new TObjectIntHashMap<>();
            offsetMap.put(SEMANTIC_NORMAL, -1);
            offsetMap.put(SEMANTIC_TEXCOORD, -1);
            offsetMap.put(SEMANTIC_VERTEX, -1);

            final Element triTag = (Element) meshTag.getElementsByTagName(ELEMENT_TRIANGLES).item(0);
            loadInput(triTag, semanticMap, offsetMap);
            // find positions now
            final Element vertTag = getElementById(doc, semanticMap.get(SEMANTIC_VERTEX).substring(1));
            loadInput(vertTag, semanticMap);
            // We should now have IDs for the semantics: 'POSITION', 'VERTEX', 'NORMAL', and 'TEXCOORD' if they exist.
            // 'POSITION' and 'VERTEX' are synonymous but the difference here is that 'POSITION' supplies the id for
            // the position sources and 'VERTEX' supplies the offset within the indices array.

            final TFloatList rawTextureCoords = new TFloatArrayList();
            final TFloatList rawNormals = new TFloatArrayList();

            // load the position data
            if (!semanticMap.containsKey(SEMANTIC_POSITION)) {
                throw new MalformedColladaFileException("Collada file is missing position data.");
            }
            loadSources(getElementById(doc, semanticMap.get(SEMANTIC_POSITION).substring(1)), positions);
            // make sure we got some positions
            if (positions.isEmpty()) {
                throw new MalformedColladaFileException("Positions source cannot be empty.");
            }

            // load the texture coords
            if (semanticMap.containsKey(SEMANTIC_TEXCOORD)) {
                loadSources(getElementById(doc, semanticMap.get(SEMANTIC_TEXCOORD).substring(1)), rawTextureCoords);
            }

            // load the normals
            if (semanticMap.containsKey(SEMANTIC_NORMAL)) {
                loadSources(getElementById(doc, semanticMap.get(SEMANTIC_NORMAL).substring(1)), rawNormals);
            }

            // load the indices
            loadIndices(parseIndices(triTag), offsetMap, indices, rawTextureCoords, textureCoords, rawNormals, normals);
        } catch (IOException e) {
            throw new MalformedColladaFileException("Error reading from input stream.", e);
        } catch (ParserConfigurationException | SAXException | MalformedColladaFileException e) {
            throw new MalformedColladaFileException("The specified Collada file is not valid.", e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Couldn't close input stream", e);
            }
        }

        final int vertSize = 3;
        final int texCoordsSize = textureCoords.isEmpty() ? 0 : STEP_TEXCOORD;
        final int normalSize = normals.isEmpty() ? 0 : STEP_NORMAL;

        return new Vector3f(vertSize, texCoordsSize, normalSize);
    }

    private static void loadIndices(int[] rawIndices, TObjectIntMap<String> offsets, TIntList indices,
                                    TFloatList rawTextureCoords, TFloatList textureCoords,
                                    TFloatList rawNormals, TFloatList normals) {
        final int positionOffset = offsets.get(SEMANTIC_VERTEX);
        final int texCoordsOffset = offsets.get(SEMANTIC_TEXCOORD);
        final int normalOffset = offsets.get(SEMANTIC_NORMAL);
        final int components = texCoordsOffset == -1 ? normalOffset == -1 ? 1 : 2 : 3;

        if (texCoordsOffset != -1) {
            textureCoords.fill(0, rawTextureCoords.size(), 0);
        }
        if (normalOffset != -1) {
            normals.fill(0, rawNormals.size(), 0);
        }

        for (int i = 0; i < rawIndices.length; i += components) {
            final int positionIndex = rawIndices[i + positionOffset];
            indices.add(positionIndex);
            if (texCoordsOffset != -1) {
                final int texCoordsIndex = rawIndices[i + texCoordsOffset] * STEP_TEXCOORD;
                for (int s = 0; s < STEP_TEXCOORD; s++) {
                    textureCoords.set(positionIndex * STEP_TEXCOORD + s, rawTextureCoords.get(texCoordsIndex + s));
                }
            }

            if (normalOffset != -1) {
                final int normalIndex = rawIndices[i + normalOffset] * STEP_NORMAL;
                for (int s = 0; s < STEP_NORMAL; s++) {
                    normals.set(positionIndex * STEP_NORMAL + s, rawNormals.get(normalIndex + s));
                }
            }
        }
    }

    private static int[] parseIndices(Element triTag) {
        final String[] rawIndices = triTag.getElementsByTagName(ELEMENT_INDICES).item(0).getTextContent().split(ARRAY_SEPARATOR);
        final int[] indices = new int[rawIndices.length];
        for (int i = 0; i < rawIndices.length; i++) {
            indices[i] = Integer.parseInt(rawIndices[i]);
        }
        return indices;
    }

    private static Element getElementById(Document doc, String id) {
        try {
            final XPathFactory factory = XPathFactory.newInstance();
            final XPath xp = factory.newXPath();
            final String src = String.format("//*[@id='%s']", id);
            final XPathExpression bin = xp.compile(src);
            return (Element) ((NodeList) bin.evaluate(doc, XPathConstants.NODESET)).item(0);
        } catch (XPathExpressionException e) {
            logger.log(Level.WARNING, "Couldn't obtain element " + id + " in Collada file", e);
            return null;
        }
    }

    private static void loadInput(Element parent, Map<String, String> sources) {
        loadInput(parent, sources, null);
    }

    private static void loadInput(Element parent, Map<String, String> sources, TObjectIntMap<String> offsets) {
        final NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            final Node node = nodes.item(i);
            if (!node.getNodeName().equals(ELEMENT_INPUT)) {
                continue;
            }
            final Element input = (Element) node;
            final String semantic = input.getAttribute(ATTRIBUTE_SEMANTIC);
            sources.put(semantic, input.getAttribute(ATTRIBUTE_SOURCE));
            if (offsets != null) {
                offsets.put(semantic, Integer.parseInt(input.getAttribute(ATTRIBUTE_OFFSET)));
            }
        }
    }

    private static void loadSources(Element parent, TFloatList target) {
        final String[] rawSrc = parent.getElementsByTagName(ELEMENT_FLOAT_ARRAY).item(0).getTextContent().split(ARRAY_SEPARATOR);
        for (String v : rawSrc) {
            target.add(Float.parseFloat(v));
        }
    }

    public static class MalformedColladaFileException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public MalformedColladaFileException(String msg) {
            super(msg);
        }

        public MalformedColladaFileException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }
}
