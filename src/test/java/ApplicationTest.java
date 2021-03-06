import infrastructure.Command;
import infrastructure.Node;
import infrastructure.client.RemoteClient;
import infrastructure.converter.HealthPayloadConverter;
import infrastructure.system.Leader;
import infrastructure.system.RemoteNode;
import infrastructure.system.message.HealthMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static configuration.Configuration.DEFAULT_LISTEN_PORT;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

public class ApplicationTest {
    private Leader leader;
    private List<Node> system;
    private final Random random = new Random();

    @BeforeEach
    void setUp() throws InterruptedException {
        try {
            leader = new Leader(Node.getLocalIp(), DEFAULT_LISTEN_PORT);

            system = new ArrayList<>();
            Node node = new Node(new TestConfiguration(DEFAULT_LISTEN_PORT, leader, system));
            node.joinSystem();
            system.add(node);
            Thread.sleep(1000);

            node = new Node(new TestConfiguration(11111, null, system));
            node.joinSystem();
            system.add(node);
            Thread.sleep(1000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        system.forEach(node -> {
            try {
                node.shutdown();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Thread.sleep(10_000);   // let the system free resources
    }

    @Test
    public void joinSystemTest() throws IOException {
        Node node = new Node(new TestConfiguration(randomPort(), null, system));
        node.joinSystem();

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> leader.equals(node.context.getLeader()));
    }

    @Test
    public void healthcheckTest() throws IOException, InterruptedException {
        TestConfiguration configuration = new TestConfiguration(randomPort(), null, system);
        RemoteClient<DatagramPacket> remoteClient = configuration.getDefaultClient();

        Node node = new Node(configuration);
        node.joinSystem();

        Thread.sleep(5000);
        HealthPayloadConverter healthPayloadConverter = new HealthPayloadConverter();
        verify(remoteClient, atLeastOnce())
                .unicast(eq(healthPayloadConverter.encode(Command.HEALTH, new HealthMessage(new RemoteNode(Node.getLocalIp(), node.context.listenPort)))),
                        eq(leader.ip()), eq(leader.port()));
    }

    @Test
    public void leaderElectionTest() throws IOException, InterruptedException {
        Node node = new Node(new TestConfiguration(randomPort(), null, system));
        node.joinSystem();
        system.add(node);

        system.get(0).shutdown();
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> system.get(1).context.isLeader() || system.get(2).context.isLeader());
        System.out.println();
        Thread.sleep(10_000);
    }

    @Test
    public void neighboursAssignTest() throws IOException {
        Node node = new Node(new TestConfiguration(randomPort(), null, system));
        node.joinSystem();
        system.add(node);

        await().atMost(5, TimeUnit.SECONDS)
                .until(() ->
                        system.get(1).context.getNeighbour() != null
                                && system.get(1).context.getNeighbour().equals(new RemoteNode(Node.getLocalIp(), node.context.listenPort))
                                && node.context.getNeighbour() != null
                                && node.context.getNeighbour().equals(new RemoteNode(Node.getLocalIp(), system.get(1).context.listenPort)));
    }

    @Test
    public void neighboursValidReassignTest() throws IOException, InterruptedException {
        Node node = new Node(new TestConfiguration(randomPort(), null, system));
        node.joinSystem();
        system.add(node);

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> node.context.getNeighbour().equals(new RemoteNode(Node.getLocalIp(), system.get(1).context.listenPort)));

        node.shutdown();
        Thread.sleep(10_000);

        await().atMost(15, TimeUnit.SECONDS).until(() -> system.get(1).context.getNeighbour() == null);
    }

    @Test
    public void dataNodeCrashTest() throws IOException, InterruptedException {
        int port = randomPort();
        Node node = new Node(new TestConfiguration(port, null, system));
        node.joinSystem();
        node.shutdown();

        await().atMost(15, TimeUnit.SECONDS).until(() ->
                !system.get(0).context.getLeaderContext()
                        .aliveNodes.containsKey(new RemoteNode(Node.getLocalIp(), port)));
    }

    private int randomPort() {
        return random.nextInt(55_535) + 10_000;
    }
}
