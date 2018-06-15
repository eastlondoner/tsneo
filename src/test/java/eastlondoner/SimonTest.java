package eastlondoner;

import junit.framework.TestCase;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.net.URL;

public class SimonTest extends TestCase
{

    public SimonTest( String testName )
    {
        super( testName );
    }

    public void test() throws Exception
    {
        URL url = new URL( "https://images.esellerpro.com/2133/I/116/57/FOG923-animation.gif" );
        byte[] bytes = IOUtils.toByteArray( url );

        try
        {
            String asciistring = EncodingUtil.getAsciiString( Base64.encodeBase64( bytes ) );
            byte[] outbytes = Base64.decodeBase64( asciistring );
            FileUtils.writeByteArrayToFile( new File( "/tmp/test.gif" ), outbytes );
        }
        catch ( Exception var8 )
        {
            var8.printStackTrace( System.out );
        }
    }
}
