package eastlondoner.matrixmarket;

/*
 * MatrixMarketReader - a simple reader for MatrixMarket files
 *
 * Copyright (c) 2015-2015 Marco Hutter - http://www.javagl.de
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

/*
MODIFIED HEAVILY BY ANDREW JEFFERSON FOR USE WITH Sparse Graph Data
 */

import eastlondoner.GraphImporter;
import eastlondoner.extensions.GuardedSpliterator;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;
import java.util.stream.StreamSupport;


/**
 * Simple implementation of a reader for MatrixMarket files
 */
public class ImporterMM extends GraphImporter
{
    public ImporterMM(Supplier<Reader> nodeReaderFn, Supplier<Reader> matrixReaderFn){
        super(nodeReaderFn, matrixReaderFn);
    }

    public Pair<Stream<Node>, Stream<Relationship>> execute() throws IOException {

        final LineNumberReader nodesReader = nodeReaderFn.get();
        final Supplier<String> nodeLineSupplier = buildLineSupplier(nodesReader);
        final AtomicInteger lineNumber = new AtomicInteger(0);

        final GuardedSpliterator<Node> nodeSpliterator = new GuardedSpliterator<>(() -> {
            String line;

            while (true) {
                line = nodeLineSupplier.get();
                if (line == null) {
                    break;
                }
                if (line.startsWith("%")) {
                    continue;
                }
                Map<String, Value> nodeProperties = Stream.of(
                        new SimpleEntry<>("name", Values.value(line))
                ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

                return new InternalNode(lineNumber.getAndIncrement(), NODE_ARRAY, nodeProperties);
            }
            return null;
        });

        Stream<Node> nodeStream = StreamSupport.stream(nodeSpliterator, false);

        // Now do the relationships
        final MatrixDescription matrixDescription = new MatrixDescription();

        final LineNumberReader inputReader = relationshipReaderFn.get();



        boolean firstLine = true;
        String sizeLine = null;
        String headerLine;

        final Supplier<String> lineSupplier = buildLineSupplier(inputReader);

        while (true) {
            headerLine = lineSupplier.get();
            if (headerLine == null) {
                break;
            }

            if (!firstLine && headerLine.startsWith("%")) {
                continue;
            }


            if (firstLine) {
                String tokens[] = headerLine.split("\\s+");
                if (tokens.length != 5) {
                    throw new IOException(
                            "Expected 5 tokens in the first line, but found "
                                    + tokens.length + ": " + headerLine);
                }
                validateFirstToken(tokens[0]);
                validateObject(tokens[1]);
                matrixDescription.setFormat(parseFormat(tokens[2]));

                // TODO The ARRAY format should also be supported...
                if (matrixDescription.getFormat() != Format.COORDINATE) {
                    throw new IOException("Only COORDINATE format is supported");
                }

                matrixDescription.setField(parseField(tokens[3]));
                matrixDescription.setSymmetry(parseSymmetry(tokens[4]));
                firstLine = false;
                continue;
            }
            if (headerLine.startsWith("%"))
            {
                continue;
            }

            String tokens[] = headerLine.split("\\s+");
            if (tokens.length != 3) {
                throw new IOException(
                        "Expected 3 tokens in the first line, but found "
                                + tokens.length + ": " + headerLine);
            }
            sizeLine = headerLine;
            break;

        }
        if(firstLine || sizeLine == null) {
            throw new RuntimeException("Problem reading Matrix Market header information");
        }
        initSize(matrixDescription, sizeLine);

        Function<String, Relationship> parsingFunction = getParsingFunction(matrixDescription);

        final GuardedSpliterator<Relationship> relationshipSpliterator = new GuardedSpliterator<>(() -> {
            String line;
            while (true) {
                line = lineSupplier.get();

                if (line == null) {
                    break;
                }

                if (line.startsWith("%")) {
                    continue;
                }

                return parsingFunction.apply(line);
            }
            return null;
        });

        Stream<Relationship> relStream = StreamSupport.stream(relationshipSpliterator, false);

        return new ImmutablePair<>(nodeStream, relStream);

    }

    private static Function<String, Relationship> getParsingFunction(MatrixDescription matrixDescription) {
        Field field = matrixDescription.getField();
        switch (field)
        {
            case REAL:
                return ImporterMM::processReal;
            case COMPLEX:
                return ImporterMM::processComplex;
            case INTEGER:
                return ImporterMM::processInteger;
            case PATTERN:
                throw new NotImplementedException("PATTERN not supported");

            default:
                // Should never happen:
                throw new AssertionError("Invalid field " + field);

        }
    }

    /**
     * Process a {@link Field#REAL} value from the current line
     */
    private static Relationship processReal(String line)
    {
        String tokens[] = line.split("\\s+");
        try {
            if (tokens.length != 3)
            {
                System.out.println(Arrays.toString(tokens));
                throw new IOException("Expected matrix entry of the form "
                        + "\"rowIndex columnIndex value\", "
                        + "but found " + line);
            }
            int row = parseInt(tokens[0]) - 1;
            int col = parseInt(tokens[1]) - 1;
            double value = parseDouble(tokens[2]);

            Map<String, Value> weight = Stream.of(
                    new SimpleEntry<>("weight", Values.value(value))
            ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));


            return new InternalRelationship(0, row, col, "LINK", weight);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Process a {@link Field#COMPLEX} value from the current line
     *
     */
    private static Relationship processComplex(String line) {
        String tokens[] = line.split("\\s+");
        try {
            if (tokens.length != 4) {
                throw new IOException("Expected matrix entry of the form "
                        + "\"rowIndex columnIndex realValue imagValue\", "
                        + "but found " + line);
            }
            int row = parseInt(tokens[0]) - 1;
            int col = parseInt(tokens[1]) - 1;
            double value0 = parseDouble(tokens[2]);
            double value1 = parseDouble(tokens[3]);

            if (value1 != 0) {
                throw new NotImplementedException("Complex values not supported");
            }

            Map<String, Value> weight = Stream.of(
                    new SimpleEntry<>("weight", Values.value(value0))
            ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));

            return new InternalRelationship(0, row, col, "LINK", weight);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Process a {@link Field#INTEGER} value from the current line
     *
     * @throws IOException If an IO-error occurs
     */
    private static Relationship processInteger(String line)
    {
        String tokens[] = line.split("\\s+");
        try {
            if (tokens.length != 3)
            {
                throw new IOException("Expected matrix entry of the form "
                        + "\"rowIndex columnIndex value\", "
                        + "but found " + line);
            }
            int row = parseInt(tokens[0]) - 1;
            int col = parseInt(tokens[1]) - 1;
            int value = parseInt(tokens[2]);
            Map<String, Value> weight = Stream.of(
                    new SimpleEntry<>("weight", Values.value(value))
            ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));


            return new InternalRelationship(0, row, col, "LINK", weight);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Initialize the size in the given {@link MatrixDescription} from
     * the given line
     *
     * @param matrixDescription The {@link MatrixDescription}
     * @param line The line
     * @throws IOException If an IO-error occurs
     */
    private static void initSize(
            MatrixDescription matrixDescription, String line) throws IOException
    {
        String tokens[] = line.split("\\s+");
        if (matrixDescription.getFormat() == Format.COORDINATE)
        {
            if (tokens.length != 3)
            {
                throw new IOException(
                        "For COORDINATE format, size must be of the form"
                                + "\"numRows numCols numNonZeros\", but found " + line);
            }
            int numRows = parseInt(tokens[0]);
            int numCols = parseInt(tokens[1]);
            int numNonZeros = parseInt(tokens[2]);
            matrixDescription.setSize(numRows, numCols, numNonZeros);
        }
        else if (matrixDescription.getFormat() == Format.ARRAY)
        {
            if (tokens.length != 2)
            {
                throw new IOException(
                        "For COORDINATE format, size must be of the form"
                                + "\"numRows numCols\", but found " + line);
            }
            int numRows = parseInt(tokens[0]);
            int numCols = parseInt(tokens[1]);
            int numNonZeros = numRows * numCols;
            matrixDescription.setSize(numRows, numCols, numNonZeros);
        }
        else
        {
            // May never happen
            throw new IOException("No matrix format found");

        }
    }

    /**
     * Parse an int from the given string and return it
     *
     * @param s The string
     * @return The result
     * @throws IOException If the string can not be parsed
     */
    private static int parseInt(String s) throws IOException
    {
        try
        {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e)
        {
            throw new IOException(e);
        }
    }

    /**
     * Parse a double from the given string and return it
     *
     * @param s The string
     * @return The result
     * @throws IOException If the string can not be parsed
     */
    private static double parseDouble(String s) throws IOException
    {
        try
        {
            return Double.parseDouble(s);
        }
        catch (NumberFormatException e)
        {
            throw new IOException(e);
        }
    }

    /**
     * Validate that the given token is "%%MatrixMarket", ignoring the case
     *
     * @param firstToken The token
     * @throws IOException If the token is not valid
     */
    private static void validateFirstToken(String firstToken)
            throws IOException
    {
        if (!firstToken.equalsIgnoreCase("%%MatrixMarket"))
        {
            throw new IOException(
                    "Expected \"%%MatrixMarket\", found " + firstToken);
        }
    }

    /**
     * Validate that the given string is "matrix", ignoring the case
     *
     * @param objectString The token
     * @throws IOException If the token is not valid
     */
    private static void validateObject(String objectString)
            throws IOException
    {
        if (!objectString.equalsIgnoreCase("matrix"))
        {
            throw new IOException(
                    "Expected \"matrix\", found " + objectString);
        }
    }

    /**
     * Parse a {@link Format} from the given string
     *
     * @param s The string
     * @return The {@link Format}
     * @throws IOException If the string can not be parsed
     */
    private static Format parseFormat(String s)
            throws IOException
    {
        try
        {
            return Format.valueOf(s.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {

            throw new IOException("Expected one of "
                    + Arrays.toString(Format.values()) + ", found " + s);
        }
    }

    /**
     * Parse a {@link Field} from the given string
     *
     * @param s The string
     * @return The {@link Field}
     * @throws IOException If the string can not be parsed
     */
    private static Field parseField(String s)
            throws IOException
    {
        try
        {
            return Field.valueOf(s.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {

            throw new IOException("Expected one of "
                    + Arrays.toString(Field.values()) + ", found " + s);
        }
    }

    /**
     * Parse a {@link Symmetry} from the given string
     *
     * @param s The string
     * @return The {@link Symmetry}
     * @throws IOException If the string can not be parsed
     */
    private static Symmetry parseSymmetry(String s)
            throws IOException
    {
        try
        {
            return Symmetry.valueOf(s.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {

            throw new IOException("Expected one of "
                    + Arrays.toString(Symmetry.values()) + ", found " + s);
        }
    }

}