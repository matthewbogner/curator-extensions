package com.bazaarvoice.zookeeper;

import com.bazaarvoice.zookeeper.internal.CuratorConnection;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.netflix.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ZooKeeperConnectionTest {
    private TestingServer _zooKeeperServer;

    /** All of the connection instances that we've created running the test. */
    private List<ZooKeeperConnection> _connections = Lists.newArrayList();

    @Before
    public void setup() throws Exception {
        _zooKeeperServer = new TestingServer();
    }

    @After
    public void teardown() throws Exception {
        for (ZooKeeperConnection connection : _connections) {
            Closeables.closeQuietly(connection);
        }
        Closeables.closeQuietly(_zooKeeperServer);
    }

    private ZooKeeperConfiguration newConfiguration() {
        assertNotNull("ZooKeeper testing server is null, did you forget to call super.setup()", _zooKeeperServer);

        // Set minimal configuration settings
        return new ZooKeeperConfiguration()
                .withConnectString(_zooKeeperServer.getConnectString())
                // For test case purposes don't retry at all.  This should never be done in production!!!
                .withBoundedExponentialBackoffRetry(100, 1000, 1);
    }

    private ZooKeeperConnection connect(ZooKeeperConfiguration configuration) {
        ZooKeeperConnection connection = configuration.connect();

        _connections.add(connection);

        return connection;
    }

    @Test
    public void testDefaultNamespace() {
        ZooKeeperConnection connection = connect(newConfiguration());
        assertEquals("", ((CuratorConnection) connection).getNamespace());
    }

    @Test
    public void testNoNamespace() {
        for (String namespace : new String[] {null, "", "/"}) {
            ZooKeeperConnection connection = connect(newConfiguration().withNamespace(namespace));
            assertEquals("", ((CuratorConnection) connection).getNamespace());
        }
    }

    @Test
    public void testNamespace() {
        ZooKeeperConnection connection = connect(newConfiguration().withNamespace("/parent"));
        assertEquals("/parent", ((CuratorConnection) connection).getNamespace());
    }

    @Test
    public void testNoNamespaceWithNoNamespace() {
        ZooKeeperConnection connection = connect(newConfiguration());
        for (String namespace : new String[] {null, "", "/"}) {
            ZooKeeperConnection namespaced = connection.withNamespace(namespace);
            assertEquals("", ((CuratorConnection) namespaced).getNamespace());
        }
    }

    @Test
    public void testNamespaceWithNoNamespace() {
        ZooKeeperConnection connection = connect(newConfiguration().withNamespace("/parent"));
        for (String namespace : new String[] {null, "", "/"}) {
            ZooKeeperConnection namespaced = connection.withNamespace(namespace);
            assertEquals("/parent", ((CuratorConnection) namespaced).getNamespace());
        }
    }

    @Test
    public void testNoNamespaceWithNamespace() {
        ZooKeeperConnection namespaced = connect(newConfiguration()).withNamespace("/child");
        assertEquals("/child", ((CuratorConnection) namespaced).getNamespace());
    }

    @Test
    public void testNamespaceChain2() {
        ZooKeeperConnection namespaced = connect(newConfiguration().withNamespace("/parent"))
                .withNamespace("/child");
        assertEquals("/parent/child", ((CuratorConnection) namespaced).getNamespace());
    }

    @Test
    public void testNamespaceChain3() {
        ZooKeeperConnection connection = connect(newConfiguration().withNamespace("/parent"))
                .withNamespace("/child")
                .withNamespace("/grandchild");
        assertEquals("/parent/child/grandchild", ((CuratorConnection) connection).getNamespace());
    }

    @Test
    public void testNamespaceChain4() {
        ZooKeeperConnection connection = connect(newConfiguration().withNamespace("/a/b"))
                .withNamespace("/c/d")
                .withNamespace("/e/f");
        assertEquals("/a/b/c/d/e/f", ((CuratorConnection) connection).getNamespace());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithRelativeNamespace() {
        connect(newConfiguration()).withNamespace("namespace");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEscapedNamespace() {
        connect(newConfiguration()).withNamespace("/../root");
    }

    @Test
    public void testBrokenCuratorNamespace() {
        ZooKeeperConnection connection = connect(newConfiguration().withNamespace("/parent"));
        assertEquals("/parent", ((CuratorConnection) connection).getNamespace());

        // Curator returns "" even when there's a namespace configured.  If/when this is fixed we can remove
        // our CuratorConnection._namespace and rely on Curator's CuratorFramework.getNamespace() instead.
        assertEquals("", ((CuratorConnection) connection).getCurator().getNamespace());
    }
}
