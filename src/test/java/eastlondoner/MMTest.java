package eastlondoner;

import eastlondoner.driver.BasicCypher;
import eastlondoner.matrixmarket.ImporterMM;
import eastlondoner.netdraw.ImporterVNA;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Unit test for simple App.
 */
public class MMTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public MMTest(String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( MMTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testParser() throws IOException {
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
            BasicCypher basicCypher = new BasicCypher();
            basicCypher.createMyGraph(streams);
        }

        //TODO: some assertions

    }
}
