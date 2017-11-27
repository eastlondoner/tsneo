package eastlondoner.development;


import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

public class CreateEmbeddedGraph {
    static AtomicInteger n = new AtomicInteger(0);

    public static GraphDatabaseService getEmbeddedDatabaseService() {
        GraphDatabaseFactory graphDbFactory = new GraphDatabaseFactory();
        GraphDatabaseService db = graphDbFactory.newEmbeddedDatabase(new File("data/foo.db"));
        return db;
    }

    public static void createNodes(GraphDatabaseService db){
        Label basicNode = Label.label("basic");
        RelationshipType connectedTo = RelationshipType.withName("connectedTo");
        Node node1 = db.createNode(basicNode);
        node1.setProperty("id",n.getAndIncrement());
        node1.setProperty("taste","great");

        Node node2 = db.createNode(basicNode);
        node2.setProperty("id",n.getAndIncrement());
        node2.setProperty("taste","fine");

        node1.createRelationshipTo(node2, connectedTo);
    }
}
