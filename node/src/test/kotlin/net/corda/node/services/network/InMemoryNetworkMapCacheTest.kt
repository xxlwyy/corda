package net.corda.node.services.network

import net.corda.core.getOrThrow
import net.corda.core.node.services.NetworkMapCache
import net.corda.core.node.services.ServiceInfo
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.node.utilities.transaction
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Test
import java.math.BigInteger
import kotlin.test.assertEquals

class InMemoryNetworkMapCacheTest {
    private val mockNet = MockNetwork()

    @After
    fun teardown() {
        mockNet.stopNodes()
    }

    @Test
    fun registerWithNetwork() {
        val (n0, n1) = mockNet.createTwoNodes()
        val future = n1.services.networkMapCache.addMapService(n1.network, n0.info.address, false, null)
        mockNet.runNetwork()
        future.getOrThrow()
    }

    @Test
    fun `key collision`() {
        val entropy = BigInteger.valueOf(24012017L)
        val nodeA = mockNet.createNode(null, -1, MockNetwork.DefaultFactory, true, ALICE.name, null, entropy, ServiceInfo(NetworkMapService.type))
        val nodeB = mockNet.createNode(null, -1, MockNetwork.DefaultFactory, true, BOB.name, null, entropy, ServiceInfo(NetworkMapService.type))
        assertEquals(nodeA.info.legalIdentity, nodeB.info.legalIdentity)

        mockNet.runNetwork()

        // Node A currently knows only about itself, so this returns node A
        assertEquals(nodeA.netMapCache.getNodeByLegalIdentityKey(nodeA.info.legalIdentity.owningKey), nodeA.info)

        nodeA.database.transaction {
            nodeA.netMapCache.addNode(nodeB.info)
        }
        // The details of node B write over those for node A
        assertEquals(nodeA.netMapCache.getNodeByLegalIdentityKey(nodeA.info.legalIdentity.owningKey), nodeB.info)
    }

    @Test
    fun `getNodeByLegalIdentity`() {
        val (n0, n1) = mockNet.createTwoNodes()
        val node0Cache: NetworkMapCache = n0.services.networkMapCache
        val expected = n1.info

        mockNet.runNetwork()
        val actual = node0Cache.getNodeByLegalIdentity(n1.info.legalIdentity)
        assertEquals(expected, actual)

        // TODO: Should have a test case with anonymous lookup
    }
}
