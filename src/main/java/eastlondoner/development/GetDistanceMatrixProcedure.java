package eastlondoner.development;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Procedure;

import static eastlondoner.development.CreateEmbeddedGraph.createNodes;
import static org.neo4j.procedure.Mode.SCHEMA;

/**
 * This is an example showing to get a matrix of shortest distances between all nodes
 */
public class GetDistanceMatrixProcedure
{
    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseService db;

    // This gives us a log instance that outputs messages to the
    // standard log, `neo4j.log`
    @Context
    public Log log;

    @Procedure( name = "example.allNodesMatrix", mode = SCHEMA )
    public void callCreateNodes()
    {
        log.info("ran create nodes script");
    }
}