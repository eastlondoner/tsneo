package eastlondoner;

import eastlondoner.driver.BasicCypher;
import eastlondoner.netdraw.ImporterVNA;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Unit test for simple App.
 */
public class NetdrawTest
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public NetdrawTest(String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( NetdrawTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testParser()
    {
        Supplier<Reader> readerSupplier = (Supplier<Reader>) () -> {
            try {
                return new FileReader("testdata/test.vna");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        try(ImporterVNA importer = new ImporterVNA(readerSupplier)){
            Pair<Stream<Node>, Stream<Relationship>> streams = importer.execute();
            BasicCypher basicCypher = new BasicCypher();
            basicCypher.createMyGraph(streams);
        }

        //TODO: some assertions

    }
}
