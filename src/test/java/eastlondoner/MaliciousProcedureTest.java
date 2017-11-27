package eastlondoner;

import eastlondoner.development.ExampleProcedure;
import eastlondoner.development.MaliciousProcedure;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import org.neo4j.driver.internal.value.StringValue;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.harness.junit.Neo4jRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.neo4j.driver.v1.Values.parameters;

public class MaliciousProcedureTest
{
    // This rule starts a Neo4j instance for us
    @Rule
    public Neo4jRule neo4j = new Neo4jRule()
            // This is the Procedure we want to test
            .withProcedure( MaliciousProcedure.class );

    @Test
    public void shouldFindBash() throws Throwable
    {
        // In a try-block, to make sure we close the driver after the test
        try( Driver driver = GraphDatabase.driver( neo4j.boltURI() , Config.build().withEncryptionLevel( Config.EncryptionLevel.NONE ).toConfig() ) )
        {

            Session session = driver.session();

            List<Record> resultList = session.run( "CALL badguy.sh({cmd})", parameters("cmd","which bash" ) ).list();

            resultList.forEach((x) -> x.values().forEach((y) -> Utils.print(y.asString())));
            assertTrue(resultList.get(0).values().get(0).asString().contains( "bash" ));
        }
    }
}