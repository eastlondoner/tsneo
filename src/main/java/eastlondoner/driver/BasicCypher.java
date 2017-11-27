package eastlondoner.driver;

import com.google.gson.Gson;
import eastlondoner.Utils;
import eastlondoner.development.EngineTSNE;
import org.apache.commons.lang3.tuple.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.neo4j.driver.v1.*;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.FileWriter;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.neo4j.driver.v1.Values.parameters;

public class BasicCypher {
    private static final String USERNAME = "neo4j";
    public static final String DEFAULT_PASSWORD = "neo4j";
    public static final String MY_DEFAULT_PASSWORD = "tsNeo1";
    public static final String MY_DEFAULT_URL = "bolt://localhost:7687";

    private final String password;
    private final String url;
    private Map<Long,Integer> nodeIdToIndex;

    private static class JsonNode {
        private final double x;
        private final double y;
        private final double z;
        private final String name;

        private JsonNode( double x, double y, String name )
        {
            this(x, y, 0, name);
        }
        private JsonNode( double x, double y, double z, String name )
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.name = name;
        }
    }

    private static class JsonEdge {
        private final int from;
        private final int to;
        private final int weight;

        private JsonEdge( int from, int to, int weight )
        {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
    }

    public static Driver buildDriver(String url, String password){
        System.out.println(url);
        try(Driver tempDriver = GraphDatabase.driver(url, AuthTokens.basic(USERNAME, DEFAULT_PASSWORD))) {
            try (Session session = tempDriver.session()) {
                // Wrapping Cypher in an explicit transaction provides atomicity
                // and makes handling errors much easier.
                try (Transaction tx = session.beginTransaction()) {
                    tx.run("CALL dbms.changePassword({myPassword})", parameters("myPassword", password));
                }
            }
        } catch (Exception e){
            System.out.println("Password set it seems");
        }

        return GraphDatabase.driver(url, AuthTokens.basic(USERNAME, password));
    }

    private final Driver driver;

    public BasicCypher(){
        this(MY_DEFAULT_URL, MY_DEFAULT_PASSWORD);
    }

    public BasicCypher(String url, String password){
        this.url = url;
        this.password = password;
        driver = buildDriver(url, password);
    }

    public void createMyGraph(Pair<Stream<Node>, Stream<Relationship>> streams){
        createMyGraph(streams.getLeft(), streams.getRight());
    }


    public void getDistances() {
        try(Session s = driver.session()) {
            try (Transaction tx = s.beginTransaction()) {
                StatementResult result = tx.run("\n" +
                        "MATCH ()-[l:LINK]-()\n" +
                        "WHERE l.weight is not NULL\n" +
                        "SET l.inverseWeight = 1.0/l.weight\n"
                );
                tx.success();
            }

            try (Transaction tx = s.beginTransaction()) {
                StatementResult result = tx.run("\n" +
                        "MATCH (n:Node), (p:Node)\n" +
                        "WHERE ID(n) < ID(p)\n" +
                        "CALL apoc.algo.dijkstra(n, p, 'LINK', 'inverseWeight') YIELD weight\n" +
                        "RETURN ID(n), ID(p), weight, n.name, p.name");

                Iterable<Record> iterable = () -> result;
                Stream<Record> resultStream = StreamSupport.stream(iterable.spliterator(), false);
                ;
                processSparseMatrix(resultStream.collect(Collectors.toList()));
                tx.success();
            }

            try (Transaction tx = s.beginTransaction()) {
                StatementResult result = tx.run("\n" +
                        "MATCH (n:Node)-[l:LINK]-(p:Node)\n" +
                        "WHERE ID(n) < ID(p)\n" +
                        "RETURN ID(n), ID(p), l.weight");

                Iterable<Record> iterable = () -> result;
                Stream<Record> resultStream = StreamSupport.stream(iterable.spliterator(), false);

                processLinks(resultStream.collect(Collectors.toList()));
                tx.success();
            }
        }

    }

    private void processLinks( List<Record> links )
    {
        Gson gson = new Gson();
        List<JsonEdge> edges = links.stream().map( ( l ) ->
        {
            Long fromId = l.get( 0 ).asLong();
            Long toId = l.get( 1 ).asLong();
            Integer weight = l.get( 2 ).asInt();

            int fromIdx = nodeIdToIndex.get( fromId );
            int toIdx = nodeIdToIndex.get( toId );
            JsonEdge edge = new JsonEdge( fromIdx, toIdx, weight );
            System.out.printf( "%s, %s, %s\n", fromIdx, toIdx, weight );
            return edge;
        } ).collect( Collectors.toList() );

        try
        {
            try(FileWriter writer = new FileWriter( "./edges.json" )){
                gson.toJson(edges, writer );
            }
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    private void processSparseMatrix(List<Record> resultStream) {

        nodeIdToIndex = new ConcurrentHashMap<>();
        Map<Integer, Long> indexToNodeId = new ConcurrentHashMap<>();
        Map<Long, String> nodeIdToName = new ConcurrentHashMap<>();
        Map<Integer, String> nodeIdxToName = new ConcurrentHashMap<>();

        AtomicInteger indexTracker =
                new AtomicInteger(0);

        BiFunction<Long, String, Integer> getIndex = (nodeId, nodeName) -> {
            int idx = nodeIdToIndex.computeIfAbsent(nodeId, (x) -> indexTracker.getAndIncrement());
            indexToNodeId.put(idx, nodeId);
            nodeIdToName.put( nodeId, nodeName );
            nodeIdxToName.put( idx, nodeName );
            return idx;
        };

        //TODO: this should be a reduce
        resultStream.forEach((row) -> {
            long fromId = row.get(0).asLong();
            long toId = row.get(1).asLong();
            String fromName = row.get(3).asString();
            String toName = row.get(4).asString();
            double distance = row.get(2).asDouble();
            int fromIdx = getIndex.apply(fromId, fromName);
            int toIdx = getIndex.apply(toId, toName);
        });

        int N = indexToNodeId.size();

        INDArray distanceMatrix = Nd4j.zeros(N, N);
        resultStream.forEach((row) -> {
            long fromId = row.get(0).asLong();
            long toId = row.get(1).asLong();
            double distance = row.get(2).asDouble();
            String fromName = row.get(3).asString();
            String toName = row.get(4).asString();
            int fromIdx = getIndex.apply(fromId, fromName);
            int toIdx = getIndex.apply(toId, toName);

            distanceMatrix.put(fromIdx, toIdx, distance);
            distanceMatrix.put(toIdx, fromIdx, distance);
        });

        EngineTSNE engine = new EngineTSNE(distanceMatrix, 10);
        INDArray reducedMatrix = engine.run();

        Gson gson = new Gson();
        List<JsonNode> nodes = IntStream.range( 0, N ).boxed().map( ( i ) ->
        {
            INDArray column = reducedMatrix.getColumn( i );
            String name = nodeIdxToName.get( i );
            double x = column.getDouble( 0, 0 );
            double y = column.getDouble( 1, 0 );
            double z = column.getDouble( 2, 0 );
            JsonNode node = new JsonNode( x, y, z, name );
            System.out.printf( "%s, %s, %s\n", name, x, y );
            return node;
        } ).collect( Collectors.toList() );

        try
        {
            try(FileWriter writer = new FileWriter( "./nodes.json" ))
            {
                gson.toJson( nodes, writer );
            }
        }
        catch ( IOException e )
        {
            throw new RuntimeException( e );
        }
    }

    public void createMyGraph(Stream<Node> nodeStream, Stream<Relationship> relationshipStream){

        try(Session s = driver.session()){
            try(Transaction tx = s.beginTransaction()){
                tx.run("CREATE INDEX ON :Node(identifier)");
                tx.success();
            }

            List<Map<Object, Object>> nodes = nodeStream.map((node) ->
                    Stream.of(
                            new SimpleEntry("identifier", node.id()),
                            new SimpleEntry("properties", node.asMap()),
                            new SimpleEntry("labels", node.labels())
                    ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue))
            ).collect(Collectors.toList());

            try(Transaction tx = s.beginTransaction()){

                StatementResult creates = tx.run(
                        "" +
                                "UNWIND $nodes AS node\n" +
                                "MERGE (n:Node {identifier:node.identifier})\n" +
                                "SET n += node.properties\n" +
                                "WITH n, node\n" +
                                "CALL apoc.create.addLabels(id(n), node.labels) YIELD node AS p\n" +
                                "RETURN p" +
                                "",
                        parameters( "nodes", nodes)
                );
                Utils.consume(creates);

                StatementResult rels = tx.run(
                    "\n" +
                    "UNWIND $relationships as relationship\n" +
                    "MATCH (node1:Node { identifier: relationship.startNodeId })\n" +
                    "MATCH (node2:Node { identifier: relationship.endNodeId })\n" +
                    "WITH relationship, node1, node2\n" +
                    "CALL apoc.create.relationship(node1, relationship.type, {weight:relationship.weight}, node2) YIELD rel as r\n" +
                    "RETURN r" +
                    "",
                    parameters(
                        "relationships",
                        relationshipStream.map((rel) -> {
                            Value weight = rel.get("weight");
                            Integer wt = weight.isNull() ? 1 : weight.asInt();
                            return Stream.of(
                                new SimpleEntry("startNodeId", rel.startNodeId()),
                                new SimpleEntry("endNodeId", rel.endNodeId()),
                                new SimpleEntry("type", rel.type()),
                                new SimpleEntry("weight", wt)
                            ).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
                        }).collect(Collectors.toList())
                    )
                );
                Utils.consume(rels);
                tx.success();

            } catch (Exception e){
                System.out.println(e);
            }
        }
    }
}
