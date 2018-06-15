package eastlondoner;

import eastlondoner.driver.BasicCypher;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.neo4j.driver.internal.value.StringValue;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.Values;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;
import org.neo4j.driver.v1.util.Function;

import eastlondoner.matrixmarket.ImporterMM;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.parboiled.common.ImmutableList;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.AbstractMap.SimpleEntry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.neo4j.driver.v1.Values.parameters;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void oldMain( String[] args )
    {
        System.out.println( "Hello World!" );
        BasicCypher c = new BasicCypher();

        // nextInt is normally exclusive of the top value,
        // so add 1 to make it inclusive
        ThreadLocalRandom relIntegers = ThreadLocalRandom.current();
        ThreadLocalRandom nodeIntegers = ThreadLocalRandom.current();

        List<String> personLabels = Stream.of("Node").collect(Collectors.toList());
        List<Node> nodeList = Stream.of(
                new InternalNode(nodeIntegers.nextLong(), personLabels, Stream.of(
                        new SimpleEntry<>("foo", Values.value("bar")),
                        new SimpleEntry<>("baz", Values.value("1"))
                ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue))),
                new InternalNode(nodeIntegers.nextLong(), personLabels, Stream.of(
                        new SimpleEntry<>("foo", Values.value("bar")),
                        new SimpleEntry<>("baz", Values.value("2"))
                ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue)))
        ).collect(Collectors.toList());

        Stream<SimpleEntry<Long, Long>> rels = nodeList.stream().map(n -> n.id())
                .flatMap(x -> nodeList.stream().map(n -> n.id()).map(y -> new SimpleEntry<Long, Long>(x, y)))
                .filter(p -> !p.getKey().equals(p.getValue()));

        c.createMyGraph(
            nodeList.stream(),
            rels.map(p ->
                new InternalRelationship(relIntegers.nextLong(), p.getKey(), p.getValue(), "LINKS")
            )
        );
    }

    public static void main( String[] args ) throws IOException
    {
        System.out.println( "Hello World!" );
        System.out.println( String.join(", ", args ));

        BasicCypher c;
        if ( args.length == 0 )
        {
            c = new BasicCypher();
        }
        else if ( args.length == 2 )
        {
            c = new BasicCypher( args[0], args[1] );
        }
        else
        {
            throw new NotImplementedException( "unknown arguments" );
        }

        try
        {
            c.getDistances();
        } catch ( IllegalArgumentException e ){
            if(e.getMessage().contains( "Length must be >= 1" )){
                importLesMis(c);
                c.getDistances();
            }
        }

    }

    private static void importLesMis(BasicCypher c) throws IOException
    {
        Supplier<Reader> relationshipReaderSupplier = (Supplier<Reader>) () -> {
            try {
                return new FileReader("testdata/lesmis.mtx");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        Supplier<Reader> nodeReaderSupplier = (Supplier<Reader>) () -> {
            try {
                return new FileReader("testdata/lesmis_nodename.txt");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };

        try(ImporterMM importer = new ImporterMM(nodeReaderSupplier, relationshipReaderSupplier)){
            Pair<Stream<Node>, Stream<Relationship>> streams = importer.execute();
            c.createMyGraph(streams);
        }
    }
}
