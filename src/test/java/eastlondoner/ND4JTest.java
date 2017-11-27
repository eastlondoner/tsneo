package eastlondoner;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Unit tests to demonstrate/investigate ND4j behaviour
 */
public class ND4JTest extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public ND4JTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( ND4JTest.class );
    }

    /**
     * Rigourous Test :-)
     */
    public void testApp()
    {
        assertTrue( true );
    }

    /**
     * Rigourous Test :-)
     */
    public void testDot()
    {
        INDArray ones = Nd4j.ones( 2, 2 );
        INDArray mul = Nd4j.zeros( 2, 2 );
        mul.putScalar( 0, 0, 1 );
        INDArray val = mul.mul( ones );
        assertEquals( val.getDouble( 0, 0 ), 1.0 );
        assertEquals( val.getDouble( 0, 1 ), 0.0 );
        assertEquals( val.getDouble( 1, 0 ), 0.0 );
        assertEquals( val.getDouble( 1, 1 ), 0.0 );
        assertTrue( true );

        ones = Nd4j.ones( 2, 2 );
        mul = Nd4j.zeros( 2, 2 );
        mul.putScalar( 0, 0, 1 );
        mul.putScalar( 0, 1, 2 );
        mul.putScalar( 1, 0, 3 );
        mul.putScalar( 1, 1, 4 );
        val = mul.mul( ones );
        assertEquals( val.getDouble( 0, 0 ), 1.0 );
        assertEquals( val.getDouble( 0, 1 ), 2.0 );
        assertEquals( val.getDouble( 1, 0 ), 3.0 );
        assertEquals( val.getDouble( 1, 1 ), 4.0 );
        assertTrue( true );
    }

    public void testDiv()
    {
        INDArray ones = Nd4j.ones( 2, 2 );
        INDArray mul = Nd4j.zeros( 2, 2 );
        ones.putScalar( 0, 0, 2 );

        mul.putScalar( 0, 0, 2 );
        mul.putScalar( 0, 1, 4 );
        mul.putScalar( 1, 0, 6 );
        mul.putScalar( 1, 1, 8 );
        INDArray val = mul.div( ones );
        assertEquals( val.getDouble( 0, 0 ), 1.0 );
        assertEquals( val.getDouble( 0, 1 ), 4.0 );
        assertEquals( val.getDouble( 1, 0 ), 6.0 );
        assertEquals( val.getDouble( 1, 1 ), 8.0 );
        assertTrue( true );
    }

    public void testRunningAverage()
    {
        INDArray runningAvg = Nd4j.ones( 2, 2 );
        INDArray ones = Nd4j.ones( 2, 2 );
        INDArray mul = Nd4j.zeros( 2, 2 );
        float gamma = 0.9f;
        ones = Nd4j.ones( 2, 2 );
        mul = Nd4j.zeros( 2, 2 );
        mul.putScalar( 0, 0, 1 );
        mul.putScalar( 0, 1, 2 );
        mul.putScalar( 1, 0, 3 );
        mul.putScalar( 1, 1, 4 );

        ones.muli(gamma);
        assertEquals( ones.getFloat( 0, 0 ), gamma );
        assertEquals( ones.getFloat( 0, 1 ), gamma );
        assertEquals( ones.getFloat( 1, 0 ), gamma );
        assertEquals( ones.getFloat( 1, 1 ), gamma );


    }



}
