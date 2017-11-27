/*
 Copyright 2008-2010 Gephi
 Authors : Vojtech Bardiovsky <vojtech.bardiovsky@gmail.com>
 Website : http://www.gephi.org
 This file is part of Gephi.
 DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 Copyright 2011 Gephi Consortium. All rights reserved.
 The contents of this file are subject to the terms of either the GNU
 General Public License Version 3 only ("GPL") or the Common
 Development and Distribution License("CDDL") (collectively, the
 "License"). You may not use this file except in compliance with the
 License. You can obtain a copy of the License at
 http://gephi.org/about/legal/license-notice/
 or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
 specific language governing permissions and limitations under the
 License.  When distributing the software, include this License Header
 Notice in each file and include the License files at
 /cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
 License Header, with the fields enclosed by brackets [] replaced by
 your own identifying information:
 "Portions Copyrighted [year] [name of copyright owner]"
 If you wish your version of this file to be governed by only the CDDL
 or only the GPL Version 3, indicate your decision by adding
 "[Contributor] elects to include this software in this distribution
 under the [CDDL or GPL Version 3] license." If you do not indicate a
 single choice of license, a recipient has the option to distribute
 your version of this file under either the CDDL, the GPL Version 3 or
 to extend the choice of license to its licensees as provided above.
 However, if you add GPL Version 3 code and therefore, elected the GPL
 Version 3 license, then the option applies only if the new code is
 made subject to such option by the copyright holder.
 Contributor(s):
 Portions Copyrighted 2011 Gephi Consortium.
 */

/* Original file from Gephi MODIFIED BY Andrew Jefferson */

package eastlondoner.netdraw;

import eastlondoner.extensions.GuardedSpliterator;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.LineNumberReader;
import java.io.Reader;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Netdraw .vna files importer implemented as a simple state machine due to very
 * loose specification of .vna format
 * (http://netwiki.amath.unc.edu/DataFormats/NetDrawVna).
 *
 * Original Author: Vojtech Bardiovsky
 * @author Andrew Jefferson
 */
public class ImporterVNA extends eastlondoner.GraphImporter {

    //Architecture
    private boolean cancel = false;
    // Pattern for splitting by spaces but respecting quotes.
    private static final Pattern PATTERN = Pattern.compile("[^\\s\"]+|\"([^\"]*)\"");
    private String[] nodeDataColumns;
    private String[] tieDataColumns;

    private ThreadLocalRandom relIntegers = ThreadLocalRandom.current();
    private ThreadLocalRandom nodeIntegers = ThreadLocalRandom.current();

    /**
     * States for the state machine.
     */
    private enum State {

        DEFAULT, NODE_DATA, NODE_PROPERTIES, TIE_DATA,
        NODE_DATA_DEF, NODE_PROPERTIES_DEF, TIE_DATA_DEF
    }

    /**
     * Attributes defined by the VNA file: VNA files allow some or no properties
     * to be defined for nodes and edges.
     */
    private enum Attributes {

        OTHER, NODE_X, NODE_Y, NODE_COLOR, NODE_SIZE,
        NODE_SHAPE, NODE_SHORT_LABEL, EDGE_STRENGTH
    }

    private String[] nodePropertiesLabels;

    /**
     * Declared attributes for the node properties declaration section.
     */
    private Attributes[] nodeDataAttributes;

    /**
     * Declared attributes for the edge declaration section.
     */
    private Attributes[] tieAttributes;


    public ImporterVNA(Supplier<Reader> readerFn) {
        //We read the same file twice
        //TODO:optimise the work done reading file twice
        super(readerFn, readerFn);
    }

    @Override
    public Pair<Stream<Node>, Stream<Relationship>> execute() {

        LineNumberReader nodeReader = nodeReaderFn.get();
        LineNumberReader relationshipReader = relationshipReaderFn.get();

        return new ImmutablePair<>(importNodes(nodeReader), importRelationships(relationshipReader));
    }

    private Stream<Node> importNodes(LineNumberReader reader) {
        return StreamSupport.stream(importInternal(reader), false).filter(x -> x instanceof Node).map(x -> (Node) x);
    }

    private Stream<Relationship> importRelationships(LineNumberReader reader) {
        return StreamSupport.stream(importInternal(reader), false).filter(x -> x instanceof Relationship).map(x -> (Relationship) x);
    }

    private Spliterator<Object> importInternal(LineNumberReader reader) {
        final Pattern nodeDataPattern = Pattern.compile("^\\*node data\\s*", Pattern.CASE_INSENSITIVE);
        final Pattern nodePropertiesPattern = Pattern.compile("^\\*node properties\\s*", Pattern.CASE_INSENSITIVE);
        final Pattern tieDataPattern = Pattern.compile("^\\*tie data\\s*", Pattern.CASE_INSENSITIVE);

        final State[] state = new State[]{State.DEFAULT};
        final HashSet<Long> existingNodeIds = new HashSet<>();

        final Supplier<String> getNextLine = buildLineSupplier(reader);

        return new GuardedSpliterator<>(() -> {
            String line;
            String[] split;
            while(true) {
                line = getNextLine.get();

                if (cancel || line == null || line.isEmpty()) {
                    return null;
                }
                if (nodeDataPattern.matcher(line).matches()) {
                    state[0] = State.NODE_DATA_DEF;
                    continue;
                } else if (nodePropertiesPattern.matcher(line).matches()) {
                    state[0] = State.NODE_PROPERTIES_DEF;
                    continue;
                } else if (tieDataPattern.matcher(line).matches()) {
                    state[0] = State.TIE_DATA_DEF;
                    continue;
                }
                switch (state[0]) {
                    case NODE_DATA_DEF:
                        String[] nodeDataLabels = line.split("[\\s,]+");
                        nodeDataColumns = new String[nodeDataLabels.length];
                        for (int i = 1; i < nodeDataLabels.length; i++) {
                            nodeDataColumns[i] = nodeDataLabels[i];
                        }
                        state[0] = State.NODE_DATA;
                        break;
                    case NODE_PROPERTIES_DEF:
                        // Initialize node properties labels and fill nodeAttributes
                        // if some attributes can be used for NodeDraft
                        nodePropertiesLabels = line.split("[\\s,]+");
                        nodeDataAttributes = new Attributes[nodePropertiesLabels.length];
                        for (int i = 1; i < nodePropertiesLabels.length; i++) {
                            if (nodePropertiesLabels[i].equalsIgnoreCase("x")) {
                                nodeDataAttributes[i] = Attributes.NODE_X;
                            } else if (nodePropertiesLabels[i].equalsIgnoreCase("y")) {
                                nodeDataAttributes[i] = Attributes.NODE_Y;
                            } else if (nodePropertiesLabels[i].equalsIgnoreCase("color")) {
                                nodeDataAttributes[i] = Attributes.NODE_COLOR;
                            } else if (nodePropertiesLabels[i].equalsIgnoreCase("size")) {
                                nodeDataAttributes[i] = Attributes.NODE_SIZE;
                            } else if (nodePropertiesLabels[i].equalsIgnoreCase("shortlabel")) {
                                nodeDataAttributes[i] = Attributes.NODE_SHORT_LABEL;
                            } else if (nodePropertiesLabels[i].equalsIgnoreCase("shape")) {
                                nodeDataAttributes[i] = Attributes.NODE_SHAPE;
                            } else {
                                throw new RuntimeException("Unexpected node parameter at line '" + line + "';");
                            }
                        }
                        state[0] = State.NODE_PROPERTIES;
                        break;
                    case TIE_DATA_DEF:
                        String tieDataLabels[] = line.split("[\\s,]+");
                        tieDataColumns = new String[tieDataLabels.length];
                        tieAttributes = new Attributes[tieDataColumns.length];
                        if (tieDataColumns.length < 2) {
                            throw new RuntimeException("Edge data labels definition does not contain two necessary variables ('from' and 'to').");
                        }
                        // Initialize edge labels and fill edgeAttributes if some
                        // attributes can be used for EdgeDraft
                        for (int i = 2; i < tieDataColumns.length; i++) {
                            if (tieDataLabels[i].equalsIgnoreCase("strength")) {
                                tieAttributes[i] = Attributes.EDGE_STRENGTH;
                            } else {
                                tieAttributes[i] = Attributes.OTHER;
                            }
                            tieDataColumns[i] = tieDataLabels[i];
                        }
                        state[0] = State.TIE_DATA;
                        break;
                    case NODE_DATA:
                        // new node
                        split = split(line);
                        if (split.length != nodeDataColumns.length) {
                            throw new RuntimeException("Number of labels and number of data mismatch in: '" + line + "'");
                            //break;
                        }
                        Node node = parseNode(split);
                        if(existingNodeIds.contains(node.id())){
                            throw new RuntimeException("Node already created " + Long.toString(node.id()));
                        } else {
                            existingNodeIds.add(node.id());
                        }
                        return node;
                        // parse - if parse error => LOG error
                    case NODE_PROPERTIES:
                        split = split(line);
                        if (split.length != nodePropertiesLabels.length) {
                            throw new RuntimeException("Number of labels and number of data mismatch in: '" + line + "'");
                            //break;
                        }
                        addNodeProperties(split);
                        // parse - if parse error => LOG error
                        break;
                    case TIE_DATA:
                        split = split(line);
                        if (split.length != tieDataColumns.length) {
                            throw new RuntimeException("Number of labels and number of data mismatch in: '" + line + "'");
                            //break;
                        }
                        return parseEdge(split);
                        // parse - if parse error => LOG error
                        //break;
                }
            }
        });
    }

    /**
     * Splits the line using space separator, but respecting quotes.
     */
    private String[] split(String line) {


        List<String> tokens = new ArrayList<>();
        Matcher patternMatcher = PATTERN.matcher(line);
        while (patternMatcher.find()) {
            if ((patternMatcher.group(1)) != null) {
                tokens.add(patternMatcher.group(1));
            } else {
                tokens.add(patternMatcher.group());
            }
        }
        return tokens.toArray(new String[]{});
    }

    private Node parseNode(String[] nodeData) {
        InternalNode node;
        Long id = Long.parseLong(nodeData[0]);

        IntStream range = IntStream.range(1, nodeData.length);

        Map<String, Value> nodeProperties = range.boxed().collect(Collectors.toMap(
                (i) -> nodeDataColumns[i],
                (i) -> Values.value(nodeData[i]))
        );

        node = new InternalNode(id, NODE_ARRAY, nodeProperties);
        return node;
    }

    private void addNodeProperties(String[] nodeProperties) {
        throw new RuntimeException("addNodeProperties not supported");
        /*
        NodeDraft node;
        String id = nodeProperties[0];
        if (!container.nodeExists(id)) {
            node = container.factory().newNodeDraft(id);
            container.addNode(node);
        } else {
            node = container.getNode(id);
        }
        int i = 0;
        try {
            for (i = 1; i < nodeProperties.length; i++) {
                switch (nodeDataAttributes[i]) {
                    case NODE_X:
                        node.setX(Float.parseFloat(nodeProperties[i]));
                        break;
                    case NODE_Y:
                        node.setY(Float.parseFloat(nodeProperties[i]));
                        break;
                    case NODE_COLOR:
                        node.setColor(nodeProperties[i]);
                        break;
                    case NODE_SIZE:
                        node.setSize(Float.parseFloat(nodeProperties[i]));
                        break;
                    case NODE_SHORT_LABEL:
                        node.setLabel(nodeProperties[i]);
                        break;
                }
            }
        } catch (NumberFormatException e) {
            report.logIssue(new Issue("Error parsing numerical value at '" + nodeProperties[i] + "'.", Issue.Level.WARNING));
        }
        */
    }

    private Relationship parseEdge(String[] edgeData) {

        Long fromNode = Long.parseLong(edgeData[0]);
        Long toNode = Long.parseLong(edgeData[1]);


        IntStream range = IntStream.range(2, edgeData.length);

        Map<String, Value> edgeProperties = range.boxed().collect(Collectors.toMap(
                (i) -> tieDataColumns[i],
                (i) -> Values.value(edgeData[i]))
        );

        Relationship edge = new InternalRelationship(0, fromNode, toNode, "LINK", edgeProperties);

        return edge;

    }


    public boolean cancel() {
        cancel = true;
        return true;
    }
}