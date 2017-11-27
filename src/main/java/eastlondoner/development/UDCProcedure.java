package eastlondoner.development;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.neo4j.ext.udc.UdcSettings;
import org.neo4j.ext.udc.impl.DefaultUdcInformationCollector;
import org.neo4j.ext.udc.impl.UdcInformationCollector;
import org.neo4j.graphdb.DependencyResolver;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.config.Configuration;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.core.StartupStatistics;
import org.neo4j.kernel.impl.store.id.IdGeneratorFactory;
import org.neo4j.kernel.impl.transaction.state.DataSourceManager;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Procedure;
import org.neo4j.udc.UsageData;

import static eastlondoner.development.CreateEmbeddedGraph.createNodes;
import static org.neo4j.procedure.Mode.READ;
import static org.neo4j.procedure.Mode.SCHEMA;

public class UDCProcedure
{
    public static class Result {
        public final Map<String,String> out;

        public Result( Map<String,String>  udcParams )
        {
            out = udcParams;
        }
    }
    // This field declares that we need a GraphDatabaseService
    // as context when any procedure in this class is invoked
    @Context
    public GraphDatabaseAPI db;

    // This gives us a log instance that outputs messages to the
    // standard log, `neo4j.log`
    @Context
    public Log log;

    @Procedure( name = "example.getConfig", mode = READ )
    public Stream<Result> getConfig()
    {
        DependencyResolver dependencyResolver = db.getDependencyResolver();
        Config config = dependencyResolver.resolveDependency(Config.class);

        return Arrays.stream(new Result[]{new Result(config.getParams())});
    }

    @Procedure( name = "example.getUdcData", mode = READ )
    public Stream<Result> getUdcData()
    {
        DependencyResolver dependencyResolver = db.getDependencyResolver();
        Config config = dependencyResolver.resolveDependency(Config.class);
        DataSourceManager xadsm = null;
        IdGeneratorFactory idGeneratorFactory = dependencyResolver.resolveDependency(IdGeneratorFactory.class);
        StartupStatistics startupStats = null;
        UsageData usageData = dependencyResolver.resolveDependency( UsageData.class );

        UdcInformationCollector udc = new DefaultUdcInformationCollector( config, xadsm, idGeneratorFactory,
                startupStats, usageData );

        return Arrays.stream(new Result[]{new Result(udc.getUdcParams())});
    }
}