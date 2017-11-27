package eastlondoner;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class GraphImporter implements AutoCloseable {
    protected final static List<String> NODE_ARRAY = Stream.of("Node").collect(Collectors.toList());
    private final ArrayList<Reader> readers;
    protected final Supplier<LineNumberReader> nodeReaderFn;
    protected final Supplier<LineNumberReader> relationshipReaderFn;

    public GraphImporter(Supplier<Reader> nodeReaderFn, Supplier<Reader> relationshipReaderFn) {
        this.readers = new ArrayList<Reader>(2);

        this.nodeReaderFn = () -> {
            Reader reader = nodeReaderFn.get();
            this.readers.add(reader);
            LineNumberReader lnr = new LineNumberReader(reader);
            this.readers.add(lnr);
            return lnr;
        };

        this.relationshipReaderFn = () -> {
            Reader reader = relationshipReaderFn.get();
            this.readers.add(reader);
            LineNumberReader lnr = new LineNumberReader(reader);
            this.readers.add(lnr);
            return lnr;
        };
    }

    protected static Supplier<String> buildLineSupplier(BufferedReader reader){
        return () -> {
            String line = null;
            try{
                while (reader.ready()) {
                    line = reader.readLine();
                    if (line != null) {
                        line = line.trim(); // TODO read with trimming automatically for better performance?
                    }
                    if (line != null && !line.isEmpty()) {
                        return line;
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return line;
        };
    }

    @Override
    public void close() {
        this.readers.forEach(x -> {
            try {
                x.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    public abstract Pair<Stream<Node>, Stream<Relationship>> execute() throws IOException;
}
