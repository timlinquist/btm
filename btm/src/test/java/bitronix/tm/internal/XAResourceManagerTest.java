/*
 * Bitronix Transaction Manager
 *
 * Copyright (c) 2010, Bitronix Software.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA 02110-1301 USA
 */
package bitronix.tm.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import bitronix.tm.mock.resource.MockXAResource;
import bitronix.tm.utils.Encoder;
import bitronix.tm.utils.Uid;
import junit.framework.TestCase;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;

public class XAResourceManagerTest extends TestCase {

    public void testEnlistNestedTx() throws XAException, BitronixSystemException {
        byte[] result = Encoder.intToBytes(0x80);
        Uid uid = new Uid(result);
        XAResourceManager xaResourceManager = new XAResourceManager(uid);
        XAResource xaResource = new MockXAResource(null);
        XAResourceHolderState xaResourceHolderState = mock(XAResourceHolderState.class);
        when(xaResourceHolderState.getXAResource()).thenReturn(xaResource);

        xaResourceManager.enlist(xaResourceHolderState);

        assertFalse(xaResourceManager.getAllResources().isEmpty());
        assertEquals(1, xaResourceManager.getAllResources().size());
        assertEquals(xaResourceManager.getAllResources().get(0), xaResourceHolderState);

        xaResourceHolderState.end(XAResource.TMSUCCESS);

        XAResourceHolderState notEndedXAResourceHolderState = mock(XAResourceHolderState.class);
        when(xaResourceHolderState.isEnded()).thenReturn(true);
        when(notEndedXAResourceHolderState.isEnded()).thenReturn(false);
        when(notEndedXAResourceHolderState.getUseTmJoin()).thenReturn(true);
        when(notEndedXAResourceHolderState.getXAResource()).thenReturn(xaResource);

        xaResourceManager.enlist(notEndedXAResourceHolderState);

        assertFalse(xaResourceManager.getAllResources().isEmpty());
        assertEquals(1, xaResourceManager.getAllResources().size());
        assertEquals(xaResourceManager.getAllResources().get(0), notEndedXAResourceHolderState);
    }
}
