package eastlondoner;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.neo4j.driver.v1.*;

import static eastlondoner.driver.BasicCypher.buildDriver;
import eastlondoner.driver.BasicCypher;
import static org.neo4j.driver.v1.Values.parameters;


/**
 * Unit test for simple App.
 */
public class DevelopmentIntegrationTest extends TestCase {

    private final Driver driver;

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public DevelopmentIntegrationTest(String testName)
    {
        super(testName);
        driver = buildDriver(BasicCypher.MY_DEFAULT_URL, BasicCypher.MY_DEFAULT_PASSWORD);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( DevelopmentIntegrationTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testNeo4jDriver()
    {
        // Sessions are lightweight and disposable connection wrappers.

        try (Session session = driver.session()) {
            try (Transaction tx = session.beginTransaction()){
                tx.run("CALL example.createNodes()");
                StatementResult result = tx.run("MATCH (node:basic) RETURN node");
                result.forEachRemaining(Utils::print);
                tx.success();  // Mark this write as successful.
            }
        }

        assertTrue( true );
    }
}
