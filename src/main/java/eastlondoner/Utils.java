package eastlondoner;

import org.neo4j.driver.internal.value.IntegerValue;
import org.neo4j.driver.internal.value.NodeValue;
import org.neo4j.driver.internal.value.NullValue;
import org.neo4j.driver.internal.value.RelationshipValue;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

import java.io.PrintStream;


public class Utils {
    static PrintStream logOutput = System.out;
    /**
     * Logs
     * @param node
     */
    public static void print(Node node){
        node.labels().forEach((l) -> logOutput.printf("LABEL: %s\n", l));
        node.asMap().forEach((key, value) -> logOutput.printf("PROPERTY: %s: %s\n", key, value));
    }
    public static void print(String str){
        logOutput.println(str);
    }
    public static void print(Record record){
        //logOutput.println(record.getClass());
        record.values().forEach(Utils::print);
        //record.fields().forEach((pair) -> System.out.printf("FIELDS: %s: %s\n", pair.key(), pair.value()));
    }
    public static void print(Relationship relationship){
        //logOutput.println(record.getClass());
        logOutput.printf("RELATIONSHIP: %s\n", relationship.type());
        logOutput.printf("%s -> %s\n", relationship.startNodeId(), relationship.endNodeId());
        relationship.asMap().forEach((key, value) -> logOutput.printf("PROPERTY: %s: %s\n", key, value));
        //record.fields().forEach((pair) -> System.out.printf("FIELDS: %s: %s\n", pair.key(), pair.value()));
    }
    public static void print(Value value){
        if(value instanceof NodeValue){
            print(value.asNode());
        } else if (value instanceof IntegerValue) {
            print(value.toString());
        } else if (value instanceof RelationshipValue) {
            print(value.asRelationship());
        } else if (value instanceof NullValue) {
            print("NULL");
        } else {
            print(value.asString());
            throw new RuntimeException(String.format("Unknown how to print type: %s", value.getClass()));
        }
    }
    public static void consume(StatementResult result){
        result.forEachRemaining(Utils::print);
    }
}
