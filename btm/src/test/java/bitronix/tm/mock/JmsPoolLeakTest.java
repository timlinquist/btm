package bitronix.tm.mock;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.mock.resource.jms.MockXAConnectionFactory;

import bitronix.tm.resource.jms.PoolingConnectionFactory;
import junit.framework.TestCase;

import javax.jms.XAConnection;


import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for Leaks that might be produced when an exception is thrown while growing the pool
 */
public class JmsPoolLeakTest extends TestCase
{
    private PoolingConnectionFactory pcf;

    protected void setUp() throws Exception {
        TransactionManagerServices.getConfiguration().setJournal("null").setGracefulShutdownInterval(0);
        TransactionManagerServices.getTransactionManager();
    }

    protected void tearDown() throws Exception {
        pcf.close();
        TransactionManagerServices.getTransactionManager().shutdown();
    }
    public void testLeak() throws Exception{
        pcf = new PoolingConnectionFactory();
        pcf.setMinPoolSize(3);
        pcf.setMaxPoolSize(6);
        pcf.setMaxIdleTime(1);
        pcf.setClassName(MockXAConnectionFactory.class.getName());
        pcf.setUniqueName("pcf");
        pcf.setAllowLocalTransactions(true);
        pcf.setAcquisitionTimeout(1);
        MockXAConnectionFactory.resetStatus();
        MockXAConnectionFactory.setStaticConnectionsToFail(2);

        try {
            pcf.init();
        } catch (Exception ex) {
            for(XAConnection connection : MockXAConnectionFactory.getConnections()) {
                verify(connection,times(1)).close();
            }
        } finally {
            MockXAConnectionFactory.setStaticConnectionsToFail(0);
            MockXAConnectionFactory.resetStatus();
        }
    }
}
