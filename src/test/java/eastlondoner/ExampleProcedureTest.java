package eastlondoner;

import eastlondoner.development.ExampleProcedure;
import org.junit.Rule;
import org.junit.Test;
import org.neo4j.driver.v1.*;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.harness.junit.Neo4jRule;

import java.util.List;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.neo4j.driver.v1.Values.parameters;

public class ExampleProcedureTest
{
    // This rule starts a Neo4j instance for us
    @Rule
    public Neo4jRule neo4j = new Neo4jRule()
            // This is the Procedure we want to test
            .withProcedure( ExampleProcedure.class );

    @Test
    public void shouldCreateNodes() throws Throwable
    {
        // In a try-block, to make sure we close the driver after the test
        try( Driver driver = GraphDatabase.driver( neo4j.boltURI() , Config.build().withEncryptionLevel( Config.EncryptionLevel.NONE ).toConfig() ) )
        {

            // Given I've started Neo4j with the FullTextIndex procedure class
            //       which my 'neo4j' rule above does.
            Session session = driver.session();

            // When I use the index procedure to index a node
            session.run( "CALL example.createNodes()", parameters( ) );

            // Then I can search for that node with lucene query syntax
            StatementResult result = session.run( "MATCH (node:basic) RETURN node"  );
            List<Record> resultList = result.list();
            assertEquals(2,  resultList.size() );
            resultList.forEach((x) -> Utils.print(x));
        }
    }
}